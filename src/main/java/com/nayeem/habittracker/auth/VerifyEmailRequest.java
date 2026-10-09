package com.nayeem.habittracker.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyEmailRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "The token from the emailed link (after #token=)")
    private String token;

    /** Hand-written so the token never reaches a log line. */
    @Override
    public String toString() {
        return "VerifyEmailRequest[token=***]";
    }
}
