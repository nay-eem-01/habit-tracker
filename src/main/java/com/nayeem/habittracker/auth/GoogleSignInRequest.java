package com.nayeem.habittracker.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleSignInRequest {

    @NotBlank
    @Size(max = 4096)
    @Schema(description = "The credential from Google Identity Services (a Google-signed ID token)")
    private String idToken;

    @Size(max = 64)
    @Schema(description = "IANA timezone, used only when this creates the account. Defaults to UTC.",
            example = "Asia/Dhaka")
    private String timezone;

    /** Hand-written so the token never reaches a log line. */
    @Override
    public String toString() {
        return "GoogleSignInRequest[idToken=***, timezone=" + timezone + "]";
    }
}
