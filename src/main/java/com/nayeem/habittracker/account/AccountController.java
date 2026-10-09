package com.nayeem.habittracker.account;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Delete my account and everything in it",
            description = "Habits, check-ins, goals, notes, links, files, notifications and sessions — for good. "
                    + "Needs the password when the account has one.")
    @ApiResponse(responseCode = "204", description = "Deleted; the tokens stop working")
    @ApiResponse(responseCode = "400", description = "Wrong password (AUTH_WRONG_PASSWORD)")
    @ApiResponse(responseCode = "429", description = "Too many wrong passwords (RATE_LIMITED)")
    @DeleteMapping
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody DeleteAccountRequest request) {
        accountService.delete(user.id(), request);
        return ResponseEntity.noContent().build();
    }
}
