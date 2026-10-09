package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @MaxBytes(72)
    @Schema(description = "Required when the account has a password")
    private String currentPassword;

    @NotBlank
    @Size(min = 8)
    @MaxBytes(72)
    private String newPassword;

    /** Hand-written so no password reaches a log line. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
