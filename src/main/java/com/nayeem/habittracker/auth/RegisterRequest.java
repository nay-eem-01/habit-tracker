package com.nayeem.habittracker.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank
    @Email
    @Size(max = 320)
    @Schema(example = "nayeem@example.com")
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    @Size(min = 2, max = 100)
    @Schema(example = "Nayeem")
    private String name;

    @Size(max = 64)
    @Schema(description = "IANA timezone; decides what \"today\" is for check-ins. Defaults to UTC.",
            example = "Asia/Dhaka")
    private String timezone;

    /** Hand-written so the password never reaches a log line. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, name=" + name + ", timezone=" + timezone + "]";
    }
}
