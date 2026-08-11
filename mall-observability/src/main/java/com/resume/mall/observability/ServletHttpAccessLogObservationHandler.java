package com.resume.mall.observability;

import com.resume.mall.common.UserHeaders;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import java.util.concurrent.TimeUnit;

final class ServletHttpAccessLogObservationHandler
        implements ObservationHandler<ServerRequestObservationContext>, Ordered {
    private static final Class<StartedAt> STARTED_AT_KEY = StartedAt.class;
    private static final Class<TraceId> TRACE_ID_KEY = TraceId.class;

    private final Tracer tracer;
    private final HttpAccessLogProperties properties;

    ServletHttpAccessLogObservationHandler(Tracer tracer, HttpAccessLogProperties properties) {
        this.tracer = tracer;
        this.properties = properties;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    @Override
    public void onStart(ServerRequestObservationContext context) {
        HttpServletRequest request = context.getCarrier();
        if (request == null || properties.isExcluded(request.getRequestURI())) {
            return;
        }
        context.put(STARTED_AT_KEY, new StartedAt(System.nanoTime()));
        String traceId = AccessLogSupport.currentTraceId(tracer, context);
        if (traceId != null) {
            context.put(TRACE_ID_KEY, new TraceId(traceId));
            HttpServletResponse response = context.getResponse();
            if (response != null && !response.isCommitted()) {
                response.setHeader(AccessLogSupport.TRACE_ID_HEADER, traceId);
            }
        }
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        StartedAt startedAt = context.get(STARTED_AT_KEY);
        HttpServletRequest request = context.getCarrier();
        if (startedAt == null || request == null) {
            return;
        }
        HttpServletResponse response = context.getResponse();
        Throwable failure = context.getError();
        int status = response == null ? 500 : response.getStatus();
        if (failure != null && status < 400) {
            status = 500;
        }
        TraceId traceId = context.get(TRACE_ID_KEY);
        String traceIdValue = traceId == null
                ? AccessLogSupport.currentTraceId(tracer, context)
                : traceId.value();
        AccessLogSupport.log(
                request.getMethod(),
                request.getRequestURI(),
                status,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt.value()),
                traceIdValue,
                AccessLogSupport.safeHeader(request.getHeader(AccessLogSupport.IDEMPOTENCY_KEY)),
                AccessLogSupport.safeHeader(request.getHeader(UserHeaders.USER_ID)),
                failure,
                properties);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private record StartedAt(long value) {
    }

    private record TraceId(String value) {
    }
}
