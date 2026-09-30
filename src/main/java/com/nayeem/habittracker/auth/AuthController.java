package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.response.HttpResponse;
import com.nayeem.habittracker.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;

    @Operation(summary = "Create an email/password account and sign in")
    @ApiResponse(responseCode = "201", description = "Account created; access token returned")
    @ApiResponse(responseCode = "400", description = "Invalid fields or unknown timezone")
    @ApiResponse(responseCode = "409", description = "Email already registered (USER_EMAIL_TAKEN)")
    @PostMapping("/register")
    ResponseEntity<HttpResponse> register(@Valid @RequestBody RegisterRequest request) {
        return HttpResponse.of(HttpStatus.CREATED, "Account created", authService.register(request));
    }

    @Operation(summary = "Sign in with email and password")
    @ApiResponse(responseCode = "200", description = "Signed in; access token returned")
    @ApiResponse(responseCode = "401", description = "Wrong email or password (AUTH_INVALID_CREDENTIALS)")
    @PostMapping("/login")
    ResponseEntity<HttpResponse> login(@Valid @RequestBody LoginRequest request) {
        return HttpResponse.ok("Signed in", authService.login(request));
    }

    @Operation(summary = "The signed-in user")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    @SecurityRequirement(name = AppConstants.JWT_TOKEN)
    @GetMapping("/me")
    ResponseEntity<HttpResponse> me(@AuthenticationPrincipal AuthUser user) {
        return HttpResponse.ok("Current user", authService.me(user.id()));
    }
}
