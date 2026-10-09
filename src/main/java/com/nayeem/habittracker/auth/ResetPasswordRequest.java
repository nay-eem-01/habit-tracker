package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "The token from the emailed link (after #token=)")
    private String token;

    @NotBlank
    @Size(min = 8)
    @MaxBytes(72)
    private String newPassword;

    /** Hand-written so neither the token nor the password reaches a log line. */
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, newPassword=***]";
    }
}
