package com.nayeem.habittracker.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    @Size(max = 320)
    @Schema(example = "nayeem@example.com")
    private String email;
}
