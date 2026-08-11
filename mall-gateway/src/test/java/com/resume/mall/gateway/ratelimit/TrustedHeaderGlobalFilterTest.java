package com.resume.mall.gateway.ratelimit;

import com.resume.mall.common.UserHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedHeaderGlobalFilterTest {
    @Test
    void removesCallerControlledIdentityAndForwardingHeaders() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        TrustedHeaderGlobalFilter filter = new TrustedHeaderGlobalFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .remoteAddress(new InetSocketAddress("203.0.113.10", 12345))
                        .header(UserHeaders.USER_ID, "999")
                        .header(UserHeaders.USER_ROLE, "ADMIN")
                        .header("X-Real-IP", "198.51.100.99")
                        .header("X-Forwarded-For", "198.51.100.99")
                        .build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, filtered -> {
            captured.set(filtered);
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(UserHeaders.USER_ID)).isNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(UserHeaders.USER_ROLE)).isNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Forwarded-For")).isNull();
        assertThat(captured.get().getRequest().getHeaders()
                .getFirst(GatewayRateLimitConstants.REAL_IP_HEADER)).isEqualTo("203.0.113.10");
    }

    @Test
    void acceptsForwardedAddressOnlyFromConfiguredProxy() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setTrustedProxies(Set.of("127.0.0.1"));
        TrustedHeaderGlobalFilter filter = new TrustedHeaderGlobalFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                        .header("X-Forwarded-For", "198.51.100.8, 127.0.0.1")
                        .build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, filtered -> {
            captured.set(filtered);
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertThat(captured.get().getRequest().getHeaders()
                .getFirst(GatewayRateLimitConstants.REAL_IP_HEADER)).isEqualTo("198.51.100.8");
    }
}
