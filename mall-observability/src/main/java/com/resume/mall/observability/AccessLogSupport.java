package com.resume.mall.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.HttpStatusCode;

import java.util.concurrent.ThreadLocalRandom;

final class AccessLogSupport {
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("mall.access");
    private AccessLogSupport() {
    }

    static String currentTraceId(Tracer tracer) {
        Span span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }

    static String currentTraceId(Tracer tracer, Observation observation) {
        String traceId = currentTraceId(tracer);
        if (traceId != null || observation == null) {
            return traceId;
        }
        return currentTraceId(tracer, observation.getContextView());
    }

    static String currentTraceId(Tracer tracer, Observation.ContextView context) {
        String traceId = currentTraceId(tracer);
        if (traceId != null || context == null) {
            return traceId;
        }
        TracingObservationHandler.TracingContext tracingContext = context
                .get(TracingObservationHandler.TracingContext.class);
        Span span = tracingContext == null ? null : tracingContext.getSpan();
        return span == null ? null : span.context().traceId();
    }

    static String safeHeader(String value) {
        return LogValues.safe(value);
    }

    static boolean shouldLog(HttpAccessLogProperties properties, int status, long durationMs, Throwable failure) {
        if (failure != null || status >= 400 || durationMs >= properties.getSlowThreshold().toMillis()) {
            return true;
        }
        double sampleRate = properties.getSuccessSampleRate();
        return sampleRate >= 1.0 || (sampleRate > 0.0 && ThreadLocalRandom.current().nextDouble() < sampleRate);
    }

    static void log(
            String method,
            String path,
            int status,
            long durationMs,
            String traceId,
            String requestId,
            String userId,
            Throwable failure,
            HttpAccessLogProperties properties) {
        if (!shouldLog(properties, status, durationMs, failure)) {
            return;
        }

        LoggingEventBuilder event;
        if (failure != null || HttpStatusCode.valueOf(status).is5xxServerError()) {
            event = ACCESS_LOG.atError();
        } else if (durationMs >= properties.getSlowThreshold().toMillis()) {
            event = ACCESS_LOG.atWarn();
        } else {
            event = ACCESS_LOG.atInfo();
        }

        event.addKeyValue("event", "http_request_completed")
                .addKeyValue("httpMethod", method)
                .addKeyValue("path", path)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs);
        addIfPresent(event, "traceId", traceId);
        addIfPresent(event, "requestId", requestId);
        addIfPresent(event, "userId", userId);
        if (failure != null) {
            event.setCause(failure);
        }
        event.log("HTTP request completed");
    }

    private static void addIfPresent(LoggingEventBuilder event, String key, String value) {
        if (value != null && !value.isBlank()) {
            event.addKeyValue(key, value);
        }
    }
}
