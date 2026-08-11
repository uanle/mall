package com.resume.mall.observability;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogSupportTest {
    @Test
    void sanitizesAndBoundsUntrustedHeaderValues() {
        String value = "request\r\n" + "x".repeat(200);

        String sanitized = AccessLogSupport.safeHeader(value);

        assertThat(sanitized).doesNotContain("\r", "\n").hasSize(128);
    }

    @Test
    void alwaysLogsErrorsAndSlowRequestsWhenSuccessSamplingIsDisabled() {
        HttpAccessLogProperties properties = new HttpAccessLogProperties();
        properties.setSuccessSampleRate(0.0);
        properties.setSlowThreshold(Duration.ofMillis(500));

        assertThat(AccessLogSupport.shouldLog(properties, 200, 20, null)).isFalse();
        assertThat(AccessLogSupport.shouldLog(properties, 400, 20, null)).isTrue();
        assertThat(AccessLogSupport.shouldLog(properties, 200, 500, null)).isTrue();
        assertThat(AccessLogSupport.shouldLog(properties, 200, 20, new RuntimeException("boom"))).isTrue();
    }
}
