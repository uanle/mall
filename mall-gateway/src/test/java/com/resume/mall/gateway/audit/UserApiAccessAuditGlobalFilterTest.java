package com.resume.mall.gateway.audit;

import com.resume.mall.gateway.ratelimit.GatewayRateLimitConstants;
import com.resume.mall.common.UserHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserApiAccessAuditGlobalFilterTest {
    @Test
    void recordsCompletedApiRequestWithoutSensitiveHeaders() throws InterruptedException {
        UserApiAccessAuditProperties properties = new UserApiAccessAuditProperties();
        CapturingRepository repository = new CapturingRepository();
        UserApiAccessAuditGlobalFilter filter = new UserApiAccessAuditGlobalFilter(properties, repository);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/seckill/1001/reserve")
                        .header("Authorization", "Bearer secret")
                        .header("Idempotency-Key", "request-1")
                        .header(GatewayRateLimitConstants.REAL_IP_HEADER, "203.0.113.8")
                        .build());
        exchange.getAttributes().put(GatewayRateLimitConstants.AUTHENTICATED_USER_ID_ATTRIBUTE, 2L);
        exchange.getAttributes().put(GatewayRateLimitConstants.AUTHENTICATED_USER_ROLE_ATTRIBUTE, "USER");

        filter.filter(exchange, filtered -> {
            filtered.getResponse().setStatusCode(HttpStatus.CREATED);
            filtered.getResponse().getHeaders().set("X-Trace-Id", "trace-1");
            return Mono.empty();
        }).block();

        assertThat(repository.await()).isTrue();
        UserApiAccessLog log = repository.saved.get();
        assertThat(log.userId()).isEqualTo(2L);
        assertThat(log.userRole()).isEqualTo("USER");
        assertThat(log.httpMethod()).isEqualTo("POST");
        assertThat(log.path()).isEqualTo("/api/seckill/1001/reserve");
        assertThat(log.status()).isEqualTo(201);
        assertThat(log.success()).isTrue();
        assertThat(log.traceId()).isEqualTo("trace-1");
        assertThat(log.requestId()).isEqualTo("request-1");
        assertThat(log.clientIp()).isEqualTo("203.0.113.8");
    }

    @Test
    void ignoresNonApiRequests() throws InterruptedException {
        UserApiAccessAuditProperties properties = new UserApiAccessAuditProperties();
        CapturingRepository repository = new CapturingRepository();
        UserApiAccessAuditGlobalFilter filter = new UserApiAccessAuditGlobalFilter(properties, repository);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health").build());

        filter.filter(exchange, filtered -> Mono.empty()).block();

        assertThat(repository.awaitBriefly()).isFalse();
    }

    @Test
    void recordsLoginUserFromResponseHeaders() throws InterruptedException {
        UserApiAccessAuditProperties properties = new UserApiAccessAuditProperties();
        CapturingRepository repository = new CapturingRepository();
        UserApiAccessAuditGlobalFilter filter = new UserApiAccessAuditGlobalFilter(properties, repository);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());

        filter.filter(exchange, filtered -> {
            filtered.getResponse().setStatusCode(HttpStatus.OK);
            filtered.getResponse().getHeaders().set(UserHeaders.USER_ID, "2");
            filtered.getResponse().getHeaders().set(UserHeaders.USER_ROLE, "USER");
            return Mono.empty();
        }).block();

        assertThat(repository.await()).isTrue();
        UserApiAccessLog log = repository.saved.get();
        assertThat(log.userId()).isEqualTo(2L);
        assertThat(log.userRole()).isEqualTo("USER");
        assertThat(log.path()).isEqualTo("/api/auth/login");
        assertThat(log.success()).isTrue();
    }

    private static class CapturingRepository implements UserApiAccessLogRepository {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<UserApiAccessLog> saved = new AtomicReference<>();

        @Override
        public void save(UserApiAccessLog log) {
            saved.set(log);
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(2, TimeUnit.SECONDS);
        }

        boolean awaitBriefly() throws InterruptedException {
            return latch.await(100, TimeUnit.MILLISECONDS);
        }

        @Override
        public long count(Long userId, String httpMethod, String pathLike, Integer status, Boolean success,
                          String traceId, String requestId, LocalDateTime createdFrom, LocalDateTime createdTo) {
            return 0;
        }

        @Override
        public List<UserApiAccessLog> page(long offset, long pageSize, Long userId, String httpMethod,
                                           String pathLike, Integer status, Boolean success, String traceId,
                                           String requestId, LocalDateTime createdFrom, LocalDateTime createdTo) {
            return List.of();
        }
    }
}
