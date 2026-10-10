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

    @Operation(summary = "Sign in (or up) with Google",
            description = "Send the ID token Google Identity Services gives the page. Finds the account by Google id, "
                    + "then by email (linking it), else creates one. Linking an account whose email was never "
                    + "confirmed removes its password and signs it out everywhere first.")
    @ApiResponse(responseCode = "200", description = "Signed in; access token in the body, refresh token cookie set")
    @ApiResponse(responseCode = "401", description = "Invalid token (AUTH_INVALID_GOOGLE_TOKEN) or unverified Google "
            + "email (AUTH_GOOGLE_EMAIL_UNVERIFIED)")
    @PostMapping("/google")
    ResponseEntity<HttpResponse> google(@Valid @RequestBody GoogleSignInRequest request) {
        return withRefreshCookie(HttpStatus.OK, "Signed in with Google", authService.googleSignIn(request));
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

    @Operation(summary = "Email a password reset link",
            description = "Always 202 with the same message, whether or not the email has an account. The link is "
                    + "<frontend>/reset-password#token=…, valid 30 minutes, single use. At most one email a minute and "
                    + "5 an hour per account. A Google-only account gets a link to set its first password.")
    @ApiResponse(responseCode = "202", description = "Accepted (an email is sent only if the account exists)")
    @ApiResponse(responseCode = "400", description = "Not an email address (VALIDATION_FAILED)")
    @PostMapping("/password/forgot")
    ResponseEntity<HttpResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return HttpResponse.of(HttpStatus.ACCEPTED,
                "If an account exists for that email, a reset link is on its way", null);
    }

    @Operation(summary = "Choose a new password with the emailed token, and sign in",
            description = "Signs the account out on every other device and retires its other reset links.")
    @ApiResponse(responseCode = "200", description = "Password set; signed in (access token in the body, refresh cookie set)")
    @ApiResponse(responseCode = "400", description = "Invalid, expired or used link (AUTH_INVALID_RESET_TOKEN), "
            + "or a password under 8 characters (VALIDATION_FAILED)")
    @PostMapping("/password/reset")
    ResponseEntity<HttpResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return withRefreshCookie(HttpStatus.OK, "Password reset", authService.resetPassword(request));
    }

    @Operation(summary = "Change my password",
            description = "Needs the current password. Signs out every other session and retires reset links; this "
                    + "session gets a fresh token pair. An account that only signs in with Google has no password yet: "
                    + "409 — it sets one through \"forgot password\" (or, once Google sign-in exists, here after a "
                    + "fresh Google sign-in).")
    @ApiResponse(responseCode = "200", description = "Changed; new access token in the body, new refresh cookie set")
    @ApiResponse(responseCode = "400", description = "Wrong current password (AUTH_WRONG_PASSWORD), "
            + "or a new password under 8 characters (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "409", description = "The account has no password yet (AUTH_PASSWORD_NOT_SET)")
    @SecurityRequirement(name = AppConstants.JWT_TOKEN)
    @PostMapping("/password/change")
    ResponseEntity<HttpResponse> changePassword(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        return withRefreshCookie(HttpStatus.OK, "Password changed", authService.changePassword(user.id(), request));
    }

    @Operation(summary = "Confirm my email address with the emailed token",
            description = "Public: the link may be opened on a device that isn't signed in. Single use, valid 24 hours.")
    @ApiResponse(responseCode = "200", description = "Email confirmed")
    @ApiResponse(responseCode = "400", description = "Invalid, expired or used link (AUTH_INVALID_VERIFY_TOKEN)")
    @PostMapping("/email/verify")
    ResponseEntity<HttpResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getToken());
        return HttpResponse.ok("Email confirmed", null);
    }

    @Operation(summary = "Send me a new confirmation email",
            description = "One sent at sign-up already. At most one a minute and 5 an hour; past that nothing is sent.")
    @ApiResponse(responseCode = "202", description = "Accepted")
    @ApiResponse(responseCode = "409", description = "Already confirmed (AUTH_EMAIL_ALREADY_VERIFIED)")
    @SecurityRequirement(name = AppConstants.JWT_TOKEN)
    @PostMapping("/email/verification")
    ResponseEntity<HttpResponse> resendVerification(@AuthenticationPrincipal AuthUser user) {
        authService.resendVerification(user.id());
        return HttpResponse.of(HttpStatus.ACCEPTED, "A confirmation email is on its way", null);
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
