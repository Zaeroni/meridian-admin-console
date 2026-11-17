package com.meridian.admin.web;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Every error response from the API has this shape. */
public record ApiError(int status, String error, String message, List<String> details, Instant timestamp) {

    public static ApiError of(HttpStatus status, String message) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, List.of(), Instant.now());
    }

    public static ApiError of(HttpStatus status, String message, List<String> details) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, details, Instant.now());
    }
}
