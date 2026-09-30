package com.nayeem.habittracker.security;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.response.HttpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Plan §5 step 3: proves a token gets through before any business endpoint exists. Replaced by
 * {@code GET /api/auth/me} in 2.1.
 */
@Tag(name = "Auth")
@RestController
class PingController {

    @Operation(summary = "Check that an access token is accepted")
    @SecurityRequirement(name = AppConstants.JWT_TOKEN)
    @GetMapping("/api/ping")
    ResponseEntity<HttpResponse> ping(@AuthenticationPrincipal AuthUser user) {
        return HttpResponse.ok("pong", Map.of("userId", user.id()));
    }
}
