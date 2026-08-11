package com.resume.mall.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.mall.common.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class RateLimitResponseHandler implements BlockRequestHandler {
    private static final ApiResponse<Void> BODY =
            ApiResponse.fail(HttpStatus.TOO_MANY_REQUESTS.value(), "request rate limit exceeded");
    private static final byte[] FALLBACK_BODY =
            "{\"code\":429,\"message\":\"request rate limit exceeded\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public RateLimitResponseHandler(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable throwable) {
        String resource = routeId(exchange);
        recordBlocked("gateway", resource, throwable);
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .header(GatewayRateLimitConstants.RATE_LIMIT_RESOURCE_HEADER, resource)
                .bodyValue(BODY);
    }

    public Mono<Void> writeBlockedResponse(
            ServerWebExchange exchange, String layer, String resource, Throwable throwable) {
        recordBlocked(layer, resource, throwable);
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
        response.getHeaders().set(GatewayRateLimitConstants.RATE_LIMIT_RESOURCE_HEADER, resource);
        byte[] body = serializeBody();
        response.getHeaders().setContentLength(body.length);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private void recordBlocked(String layer, String resource, Throwable throwable) {
        String blockType = throwable == null ? "unknown" : throwable.getClass().getSimpleName();
        meterRegistry.counter(
                        "mall.gateway.rate.limit.blocked",
                        "layer", safeTag(layer),
                        "resource", safeTag(resource),
                        "block.type", safeTag(blockType))
                .increment();
    }

    private byte[] serializeBody() {
        try {
            return objectMapper.writeValueAsBytes(BODY);
        } catch (JsonProcessingException ex) {
            return FALLBACK_BODY;
        }
    }

    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        return route == null ? "unmatched" : route.getId();
    }

    private String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() > 80 ? value.substring(0, 80) : value;
    }
}
