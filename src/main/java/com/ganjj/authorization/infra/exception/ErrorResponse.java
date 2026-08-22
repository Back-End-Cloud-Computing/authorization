package com.ganjj.authorization.infra.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato único de erro da API. "fields" só aparece em erro de validação.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> fields,
        LocalDateTime timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null, LocalDateTime.now());
    }

    public static ErrorResponse validation(int status, String message, Map<String, String> fields) {
        return new ErrorResponse(status, "Validation Failed", message, fields, LocalDateTime.now());
    }
}
