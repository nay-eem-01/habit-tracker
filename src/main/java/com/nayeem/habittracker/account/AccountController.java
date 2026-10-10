package com.nayeem.habittracker.account;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Operation(summary = "Download all my data as JSON",
            description = "Profile, habits (archived too), every check-in, goals, notes, links and file entries "
                    + "(not the files themselves — download those one by one). Same shapes as the rest of the API.")
    @ApiResponse(responseCode = "200", description = "devhabit-export.json, as an attachment")
    @GetMapping("/export")
    ResponseEntity<AccountExport> export(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("devhabit-export.json").build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(accountService.export(user.id()));
    }

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
