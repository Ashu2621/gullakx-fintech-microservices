package com.gullakx.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The envelope every service returns.
 *
 * This previously used Lombok's {@code @Builder}, but Lombok was never declared
 * as a dependency anywhere in the build, so the module did not compile.
 * Rewritten as plain Java with static factories: identical call sites, one
 * fewer annotation processor in the build, and nothing an IDE has to be
 * configured for before the project will open.
 *
 * Null fields are omitted from the JSON, so a success response does not carry
 * an empty {@code errorCode} and a failure does not carry a null {@code data}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final String errorCode;
    private final String correlationId;
    private final long timestamp;

    private ApiResponse(boolean success, String message, T data, String errorCode, String correlationId) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
        this.correlationId = correlationId;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null, null);
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, message, null, errorCode, null);
    }

    /** Same response, tagged with a correlation id for tracing across services. */
    public ApiResponse<T> withCorrelationId(String correlationId) {
        return new ApiResponse<>(this.success, this.message, this.data, this.errorCode, correlationId);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
