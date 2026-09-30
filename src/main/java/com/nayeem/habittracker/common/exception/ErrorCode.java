package com.nayeem.habittracker.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. The frontend branches on these, not on the message.
 * Prefix by feature ({@code AUTH_}, {@code USER_}, {@code HABIT_}); add a code only when
 * something throws it.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Some fields are invalid"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You are not allowed to do this"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),

    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Session expired, please sign in again"),

    USER_EMAIL_TAKEN(HttpStatus.CONFLICT, "An account with this email already exists"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    USER_INVALID_TIMEZONE(HttpStatus.BAD_REQUEST, "Unknown timezone"),

    HABIT_NOT_FOUND(HttpStatus.NOT_FOUND, "Habit not found"),
    HABIT_INVALID_FREQUENCY(HttpStatus.BAD_REQUEST, "The schedule doesn't match the frequency type"),
    HABIT_ARCHIVED(HttpStatus.CONFLICT, "This habit is archived; unarchive it first"),

    LOG_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "You can't check in for that date");

    private final HttpStatus status;
    private final String defaultMessage;
}
