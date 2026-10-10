package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank
    @Schema(example = "nayeem@example.com")
    private String email;

    @NotBlank
    @MaxBytes(72)
    private String password;

    /** Hand-written so the password never reaches a log line. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
