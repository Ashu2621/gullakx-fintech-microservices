package com.gullakx.wallet.web;

import com.gullakx.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errors leave as machine-readable codes, not prose.
 *
 * A caller retrying a transfer needs to distinguish "you sent this wrong" from
 * "the balance was too low" without parsing an English sentence that a future
 * commit will reword.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), "Invalid request"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> missingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("MISSING_HEADER_" + ex.getHeaderName().toUpperCase().replace('-', '_'),
                        "Required header missing"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody(MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField())
                .orElse("body");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("INVALID_" + field.toUpperCase(), "Validation failed"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), "Operation refused"));
    }
}
