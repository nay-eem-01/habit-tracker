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
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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

    @Operation(summary = "Set my profile picture",
            description = "Multipart, part \"file\": JPEG, PNG or GIF up to 5 MB. Stored as the centre square, "
                    + "256 px, JPEG, with no metadata. Crop and shrink in the browser first: phone photos "
                    + "keep their rotation only in metadata, which is dropped.")
    @ApiResponse(responseCode = "200", description = "The updated user, with a new avatarUpdatedAt")
    @ApiResponse(responseCode = "413", description = "Over 5 MB (USER_AVATAR_TOO_LARGE)")
    @ApiResponse(responseCode = "415", description = "Not a JPEG, PNG or GIF picture (USER_AVATAR_INVALID)")
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<HttpResponse> setAvatar(@AuthenticationPrincipal AuthUser user,
                                           @RequestPart("file") MultipartFile file) throws IOException {
        return HttpResponse.ok("Profile picture updated", userService.setAvatar(user.id(), file.getBytes()));
    }

    @Operation(summary = "My profile picture",
            description = "A JPEG. Send the bearer token: fetch it and show the blob, keyed by the user's "
                    + "avatarUpdatedAt — a plain <img src> can't carry the header.")
    @ApiResponse(responseCode = "200", description = "The picture's bytes")
    @ApiResponse(responseCode = "404", description = "No profile picture (USER_AVATAR_NOT_FOUND)")
    @GetMapping("/avatar")
    ResponseEntity<byte[]> getAvatar(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(userService.getAvatar(user.id()));
    }

    @Operation(summary = "Remove my profile picture", description = "The app shows initials again.")
    @ApiResponse(responseCode = "204", description = "Removed, or there was none")
    @DeleteMapping("/avatar")
    ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal AuthUser user) {
        userService.deleteAvatar(user.id());
        return ResponseEntity.noContent().build();
    }
}
