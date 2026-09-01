package com.gullakx.auth.web;

import com.gullakx.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> rejected(IllegalArgumentException ex) {
        // INVALID_CREDENTIALS is a 401; everything else is a malformed request.
        // Both carry a code rather than prose, so a client can branch on the
        // outcome without matching an English sentence.
        boolean unauthorized = "INVALID_CREDENTIALS".equals(ex.getMessage());
        HttpStatus status = unauthorized ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getMessage(), unauthorized ? "Sign-in failed" : "Invalid request"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody(MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(e -> e.getField()).orElse("body");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("INVALID_" + field.toUpperCase(), "Validation failed"));
    }
}
