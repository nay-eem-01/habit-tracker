package com.nayeem.habittracker.common.response;

import com.nayeem.habittracker.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * The one response envelope every endpoint returns (see the api-conventions skill).
 * Success bodies leave {@code errorCode}, {@code correlationId} and {@code fields} null;
 * error bodies leave {@code payload} null. {@code status} is the enum name ({@code "NOT_FOUND"}):
 * Jackson 3 would otherwise write {@code HttpStatus.toString()}, i.e. {@code "404 NOT_FOUND"}.
 */
public record HttpResponse(
        String status,
        boolean success,
        String message,
        String errorCode,
        String correlationId,
        Map<String, String> fields,
        Object payload) {

    public static ResponseEntity<HttpResponse> ok(String message, Object payload) {
        return of(HttpStatus.OK, message, payload);
    }

    public static ResponseEntity<HttpResponse> of(HttpStatus status, String message, Object payload) {
        return ResponseEntity.status(status)
                .body(new HttpResponse(status.name(), true, message, null, null, null, payload));
    }

    public static HttpResponse error(ErrorCode code, String message, String correlationId,
                                     Map<String, String> fields) {
        return new HttpResponse(code.getStatus().name(), false, message, code.name(), correlationId, fields, null);
    }
}
