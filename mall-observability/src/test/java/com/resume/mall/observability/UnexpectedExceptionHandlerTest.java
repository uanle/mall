package com.resume.mall.observability;

import com.resume.mall.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;

import static org.assertj.core.api.Assertions.assertThat;

class UnexpectedExceptionHandlerTest {
    private final UnexpectedExceptionHandler handler = new UnexpectedExceptionHandler();

    @Test
    void preservesFrameworkClientErrorStatusWithoutExposingDetails() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.unexpected(new ErrorResponseException(HttpStatus.NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("resource not found");
    }

    @Test
    void returnsSafeResponseForUnexpectedExceptions() {
        ResponseEntity<ApiResponse<Void>> response = handler.unexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("internal server error");
        assertThat(response.getBody().message()).doesNotContain("boom");
    }
}
