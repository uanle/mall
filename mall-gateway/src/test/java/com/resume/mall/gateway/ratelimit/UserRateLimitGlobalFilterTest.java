package com.resume.mall.gateway.ratelimit;

import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

class UserRateLimitGlobalFilterTest {
    private static final String ROUTE_ID = "test-user-limited-route";

    private List<ParamFlowRule> originalRules;

    @BeforeEach
    void installTestRule() {
        originalRules = new ArrayList<>(ParamFlowRuleManager.getRules());
        List<ParamFlowRule> rules = new ArrayList<>(originalRules);
        rules.add(new ParamFlowRule(GatewayRateLimitConstants.userResource(ROUTE_ID))
                .setGrade(1)
                .setParamIdx(0)
                .setCount(1)
                .setDurationInSec(10));
        ParamFlowRuleManager.loadRules(rules);
    }

    @AfterEach
    void restoreRules() {
        ParamFlowRuleManager.loadRules(originalRules);
    }

    @Test
    void blocksSecondRequestForSameAuthenticatedUser() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setUserLimitedRoutes(Set.of(ROUTE_ID));
        RateLimitResponseHandler responseHandler = new RateLimitResponseHandler(
                new ObjectMapper(), new SimpleMeterRegistry());
        UserRateLimitGlobalFilter filter = new UserRateLimitGlobalFilter(properties, responseHandler);
        AtomicInteger forwarded = new AtomicInteger();

        MockServerWebExchange first = exchange(42L);
        filter.filter(first, ignored -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block();

        MockServerWebExchange second = exchange(42L);
        filter.filter(second, ignored -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(forwarded).hasValue(1);
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(second.getResponse().getBodyAsString().block())
                .contains("\"code\":429")
                .contains("request rate limit exceeded");
    }

    private MockServerWebExchange exchange(long userId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/test").build());
        Route route = Route.async()
                .id(ROUTE_ID)
                .uri(URI.create("http://localhost"))
                .predicate(ignored -> true)
                .build();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        exchange.getAttributes().put(
                GatewayRateLimitConstants.AUTHENTICATED_USER_ID_ATTRIBUTE, userId);
        return exchange;
    }
}
