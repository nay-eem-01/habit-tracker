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
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts; wait a little and try again"),

    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Session expired, please sign in again"),
    AUTH_INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired; ask for a new one"),
    // 400, not 401: the session is fine — a 401 would make the client think it was signed out
    AUTH_WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "Your current password is wrong"),
    AUTH_INVALID_VERIFY_TOKEN(HttpStatus.BAD_REQUEST, "This confirmation link is invalid or has expired; ask for a new one"),
    AUTH_EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "Your email is already confirmed"),
    AUTH_PASSWORD_NOT_SET(HttpStatus.CONFLICT, "This account has no password yet; use \"Forgot password\" to set one"),

    USER_EMAIL_TAKEN(HttpStatus.CONFLICT, "An account with this email already exists"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    USER_INVALID_TIMEZONE(HttpStatus.BAD_REQUEST, "Unknown timezone"),

    HABIT_NOT_FOUND(HttpStatus.NOT_FOUND, "Habit not found"),
    HABIT_INVALID_FREQUENCY(HttpStatus.BAD_REQUEST, "The schedule doesn't match the frequency type"),
    HABIT_QUIT_INVALID(HttpStatus.BAD_REQUEST, "That isn't possible for a quit habit"),
    HABIT_ARCHIVED(HttpStatus.CONFLICT, "This habit is archived; unarchive it first"),

    GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Goal not found"),
    GOAL_ALREADY_CLOSED(HttpStatus.CONFLICT, "This goal is already achieved or abandoned"),
    GOAL_NOT_ACTIVE(HttpStatus.CONFLICT, "Only an active goal can have habits linked to it"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    RESOURCE_INVALID(HttpStatus.BAD_REQUEST, "The fields don't fit the resource type"),

    FILE_UPLOADS_DISABLED(HttpStatus.FORBIDDEN, "File uploads are switched off for now"),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "The file is empty"),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "This resource has no file to download"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The file is too large"),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Only PNG, JPEG, WebP, GIF, PDF and text (.txt, .md) files can be uploaded"),
    FILE_QUOTA_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "Your file storage is full; delete a file first"),

    LOG_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "You can't check in for that date"),

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found");

    private final HttpStatus status;
    private final String defaultMessage;
}
