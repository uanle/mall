package com.resume.mall.observability;

import com.resume.mall.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public final class UnexpectedExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnexpectedExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            if (errorResponse.getStatusCode().is5xxServerError()) {
                logUnexpected(ex, status);
            }
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(ApiResponse.fail(status, safeFrameworkMessage(status)));
        }
        logUnexpected(ex, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(500, "internal server error"));
    }

    private void logUnexpected(Exception ex, int status) {
        LOGGER.atError()
                .addKeyValue("event", "unhandled_exception")
                .addKeyValue("status", status)
                .setCause(ex)
                .log("Unhandled request exception");
    }

    private String safeFrameworkMessage(int status) {
        return switch (status) {
            case 404 -> "resource not found";
            case 405 -> "method not allowed";
            default -> status >= 500 ? "internal server error" : "invalid request";
        };
    }
}
