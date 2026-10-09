package com.nayeem.habittracker.user;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.response.HttpResponse;
import com.nayeem.habittracker.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
class UserController {

    private final UserService userService;

    @Operation(summary = "Change my name, timezone and promotional-email choice",
            description = "A new timezone moves \"today\" for check-ins, streaks and reminders from now on; "
                    + "days already logged keep their dates.")
    @ApiResponse(responseCode = "200", description = "The updated user")
    @ApiResponse(responseCode = "400", description = "Invalid fields, or not an IANA region name (USER_INVALID_TIMEZONE)")
    @PutMapping
    ResponseEntity<HttpResponse> update(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody UpdateProfileRequest request) {
        return HttpResponse.ok("Profile updated", userService.updateProfile(user.id(), request));
    }
}
