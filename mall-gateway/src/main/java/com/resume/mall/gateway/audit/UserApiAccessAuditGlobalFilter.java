package com.resume.mall.gateway.audit;

import com.resume.mall.gateway.ratelimit.GatewayRateLimitConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class UserApiAccessAuditGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserApiAccessAuditGlobalFilter.class);
    private static final int MAX_VALUE_LENGTH = 128;

    private final UserApiAccessAuditProperties properties;
    private final UserApiAccessLogRepository repository;

    public UserApiAccessAuditGlobalFilter(
            UserApiAccessAuditProperties properties,
            UserApiAccessLogRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled() || !shouldRecord(exchange)) {
            return chain.filter(exchange);
        }
        long startNanos = System.nanoTime();
        LocalDateTime createdAt = LocalDateTime.now();
        return chain.filter(exchange)
                .doOnError(ex -> save(exchange, startNanos, createdAt, ex.getClass().getSimpleName()))
                .doOnSuccess(ignored -> save(exchange, startNanos, createdAt, null));
    }

    @Override
    public int getOrder() {
        return GatewayRateLimitConstants.TRUSTED_HEADER_FILTER_ORDER + 10;
    }

    private boolean shouldRecord(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return path.startsWith("/api/");
    }

    private void save(ServerWebExchange exchange, long startNanos, LocalDateTime createdAt, String errorType) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        int statusCode = status == null ? (errorType == null ? 200 : 500) : status.value();
        long durationMs = Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
        UserApiAccessLog log = new UserApiAccessLog(
                null,
                safe(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")),
                safe(firstHeader(exchange, "Idempotency-Key", "X-Request-Id")),
                exchange.getAttribute(GatewayRateLimitConstants.AUTHENTICATED_USER_ID_ATTRIBUTE),
                safe(exchange.getAttribute(GatewayRateLimitConstants.AUTHENTICATED_USER_ROLE_ATTRIBUTE)),
                routeId(exchange),
                exchange.getRequest().getMethod().name(),
                safe(exchange.getRequest().getURI().getPath(), 512),
                statusCode,
                statusCode >= 200 && statusCode < 400,
                durationMs,
                safe(exchange.getRequest().getHeaders().getFirst(GatewayRateLimitConstants.REAL_IP_HEADER)),
                safe(errorType),
                createdAt);

        Mono.fromRunnable(() -> repository.save(log))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, ex -> LOGGER.atWarn()
                        .addKeyValue("event", "user_api_access_log_save_failed")
                        .addKeyValue("path", log.path())
                        .setCause(ex)
                        .log("Failed to save user API access log"));
    }

    private String firstHeader(ServerWebExchange exchange, String... names) {
        for (String name : names) {
            String value = exchange.getRequest().getHeaders().getFirst(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        return route == null ? null : safe(route.getId());
    }

    private String safe(Object value) {
        return value == null ? null : safe(String.valueOf(value), MAX_VALUE_LENGTH);
    }

    private String safe(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_')
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
