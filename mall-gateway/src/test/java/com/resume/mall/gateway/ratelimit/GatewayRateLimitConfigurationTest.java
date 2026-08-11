package com.resume.mall.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.sentinel.transport.dashboard=",
                "spring.cloud.sentinel.transport.port=0"
        })
class GatewayRateLimitConfigurationTest {
    @Autowired
    private List<SentinelGatewayFilter> sentinelGatewayFilters;

    @Autowired
    private RateLimitResponseHandler responseHandler;

    @Autowired
    private RateLimitRulesHealthIndicator healthIndicator;

    @Autowired
    private GatewayRateLimitProperties properties;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void loadsOneOrderedGatewayFilterAndCustomBlockHandler() {
        assertThat(sentinelGatewayFilters).singleElement()
                .extracting(SentinelGatewayFilter::getOrder)
                .isEqualTo(-150);
        assertThat(GatewayCallbackManager.getBlockHandler()).isSameAs(responseHandler);
    }

    @Test
    void loadsEveryRequiredLocalRule() {
        Set<String> resources = GatewayRuleManager.getRules().stream()
                .map(rule -> rule.getResource())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(resources).containsAll(properties.getRequiredGatewayResources());
        assertThat(GatewayApiDefinitionManager.getApiDefinition("api-all")).isNotNull();
        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void exposesAllRateLimitedBusinessRoutes() {
        Set<String> routeIds = routeDefinitionLocator.getRouteDefinitions()
                .map(route -> route.getId())
                .collectList()
                .map(Set::copyOf)
                .block();

        assertThat(routeIds).isNotNull().containsAll(properties.getRequiredGatewayResources().stream()
                .filter(resource -> !resource.equals("api-all"))
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void routesHighRiskOperationsToDedicatedSentinelResources() {
        assertThat(matchingRoute(HttpMethod.POST, "/api/auth/login")).isEqualTo("auth-login");
        assertThat(matchingRoute(HttpMethod.GET, "/api/audit/access-logs")).isEqualTo("audit-query");
        assertThat(matchingRoute(HttpMethod.GET, "/api/products/2001")).isEqualTo("catalog-read");
        assertThat(matchingRoute(HttpMethod.POST, "/api/products")).isEqualTo("product-admin");
        assertThat(matchingRoute(HttpMethod.POST, "/api/seckill/1101/reserve"))
                .isEqualTo("seckill-reserve");
        assertThat(matchingRoute(HttpMethod.GET, "/api/seckill/activities/1101"))
                .isEqualTo("seckill-query");
        assertThat(matchingRoute(HttpMethod.POST, "/api/orders")).isEqualTo("order-create");
        assertThat(matchingRoute(HttpMethod.POST, "/api/orders/cart/checkout"))
                .isEqualTo("cart-checkout");
        assertThat(matchingRoute(HttpMethod.POST, "/api/orders/ORDER-1/payments"))
                .isEqualTo("payment-command");
        assertThat(matchingRoute(HttpMethod.POST, "/api/orders/seckill-orders/ORDER-1/payments"))
                .isEqualTo("payment-command");
        assertThat(matchingRoute(HttpMethod.PUT, "/api/orders/cart/items/1"))
                .isEqualTo("cart-command");
        assertThat(matchingRoute(HttpMethod.DELETE, "/api/orders/ORDER-1"))
                .isEqualTo("order-command");
    }

    private String matchingRoute(HttpMethod method, String path) {
        return routeLocator.getRoutes()
                .concatMap(route -> reactor.core.publisher.Mono.from(route.getPredicate()
                        .apply(MockServerWebExchange.from(
                                MockServerHttpRequest.method(method, path).build())))
                        .filter(Boolean::booleanValue)
                        .map(ignored -> route.getId()))
                .next()
                .block();
    }
}
