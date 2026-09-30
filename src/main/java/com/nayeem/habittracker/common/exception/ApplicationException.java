package com.nayeem.habittracker.common.exception;

import lombok.Getter;

/**
 * Thrown for any expected failure. The {@link ErrorCode} decides the HTTP status; the message
 * is shown to the client, so it must be safe to display.
 */
@Getter
public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApplicationException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public ApplicationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
