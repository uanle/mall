package com.resume.mall.observability;

import com.resume.mall.common.UserHeaders;
import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.Tracer;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class ReactiveHttpAccessLogWebFilter implements WebFilter, Ordered {
    private final Tracer tracer;
    private final HttpAccessLogProperties properties;

    ReactiveHttpAccessLogWebFilter(Tracer tracer, HttpAccessLogProperties properties) {
        this.tracer = tracer;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (properties.isExcluded(path)) {
            return chain.filter(exchange);
        }
        return Mono.deferContextual(contextView -> {
            long startedAt = System.nanoTime();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Observation observation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
            String traceId = AccessLogSupport.currentTraceId(tracer, observation);
            if (traceId != null) {
                exchange.getResponse().getHeaders().set(AccessLogSupport.TRACE_ID_HEADER, traceId);
            }
            return chain.filter(exchange)
                    .doOnError(failure::set)
                    .doFinally(signalType -> {
                        Throwable error = failure.get();
                        HttpStatusCode responseStatus = exchange.getResponse().getStatusCode();
                        int status = error != null
                                ? 500
                                : responseStatus == null ? 200 : responseStatus.value();
                        if (signalType == reactor.core.publisher.SignalType.CANCEL && error == null) {
                            status = 499;
                        }
                        AccessLogSupport.log(
                                exchange.getRequest().getMethod().name(),
                                path,
                                status,
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                                traceId,
                                AccessLogSupport.safeHeader(exchange.getRequest().getHeaders()
                                        .getFirst(AccessLogSupport.IDEMPOTENCY_KEY)),
                                AccessLogSupport.safeHeader(exchange.getRequest().getHeaders()
                                        .getFirst(UserHeaders.USER_ID)),
                                error,
                                properties);
                    });
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
