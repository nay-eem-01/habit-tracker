package com.nayeem.habittracker.account;

import com.nayeem.habittracker.common.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteAccountRequest {

    @MaxBytes(72)
    @Schema(description = "Required when the account has a password")
    private String password;

    /** Hand-written so the password never reaches a log line. */
    @Override
    public String toString() {
        return "DeleteAccountRequest[password=***]";
    }
}
