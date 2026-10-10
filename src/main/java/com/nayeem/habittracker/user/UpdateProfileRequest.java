package com.nayeem.habittracker.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Full replace of what a user may change about themselves. The email stays as it is. */
@Getter
@Setter
@ToString
public class UpdateProfileRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "IANA region name; the web app sends the browser's (Intl…resolvedOptions().timeZone)",
            example = "Asia/Dhaka")
    private String timezone;

    @NotNull
    @Schema(description = "Promotional email. Off unless the user turns it on.")
    private Boolean marketingEmails;
}
