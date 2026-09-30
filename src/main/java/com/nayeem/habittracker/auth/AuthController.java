package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.common.response.HttpResponse;
import com.nayeem.habittracker.security.AuthUser;
import com.nayeem.habittracker.security.SecurityProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * The access token goes in the body; the refresh token only ever travels in an httpOnly,
 * Secure, SameSite=Strict cookie scoped to {@code /api/auth}, so page scripts can't read it and
 * other sites can't make the browser send it.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping(AuthController.BASE_PATH)
@RequiredArgsConstructor
class AuthController {

    static final String BASE_PATH = "/api/auth";
    static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final SecurityProperties properties;

    @Operation(summary = "Create an email/password account and sign in")
    @ApiResponse(responseCode = "201", description = "Account created; access token in the body, refresh token cookie set")
    @ApiResponse(responseCode = "400", description = "Invalid fields or unknown timezone")
    @ApiResponse(responseCode = "409", description = "Email already registered (USER_EMAIL_TAKEN)")
    @PostMapping("/register")
    ResponseEntity<HttpResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(HttpStatus.CREATED, "Account created", authService.register(request));
    }

    @Operation(summary = "Sign in with email and password")
    @ApiResponse(responseCode = "200", description = "Signed in; access token in the body, refresh token cookie set")
    @ApiResponse(responseCode = "401", description = "Wrong email or password (AUTH_INVALID_CREDENTIALS)")
    @PostMapping("/login")
    ResponseEntity<HttpResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(HttpStatus.OK, "Signed in", authService.login(request));
    }

    @Operation(summary = "Get a new access token using the refresh token cookie; the cookie is rotated")
    @ApiResponse(responseCode = "200", description = "New access token; new refresh token cookie set")
    @ApiResponse(responseCode = "401", description = "Missing, expired, revoked or reused refresh token (AUTH_INVALID_REFRESH_TOKEN)")
    @PostMapping("/refresh")
    ResponseEntity<HttpResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApplicationException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }
        return withRefreshCookie(HttpStatus.OK, "Token refreshed", authService.refresh(refreshToken));
    }

    @Operation(summary = "Sign out: revoke the refresh token and clear its cookie")
    @ApiResponse(responseCode = "204", description = "Signed out (also when there was nothing to revoke)")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @Operation(summary = "The signed-in user")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    @SecurityRequirement(name = AppConstants.JWT_TOKEN)
    @GetMapping("/me")
    ResponseEntity<HttpResponse> me(@AuthenticationPrincipal AuthUser user) {
        return HttpResponse.ok("Current user", authService.me(user.id()));
    }

    private ResponseEntity<HttpResponse> withRefreshCookie(HttpStatus status, String message, AuthResult result) {
        ResponseCookie cookie = refreshCookie(result.refreshToken(), properties.getRefreshToken().getTtl());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(HttpResponse.of(status, message, result.body()).getBody());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.getRefreshToken().isCookieSecure())
                .sameSite("Strict")
                .path(BASE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
