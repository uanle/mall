package com.resume.mall.gateway.ratelimit;

import com.alibaba.csp.sentinel.AsyncEntry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class UserRateLimitGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRateLimitGlobalFilter.class);

    private final GatewayRateLimitProperties properties;
    private final RateLimitResponseHandler responseHandler;

    public UserRateLimitGlobalFilter(
            GatewayRateLimitProperties properties,
            RateLimitResponseHandler responseHandler) {
        this.properties = properties;
        this.responseHandler = responseHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        Long userId = exchange.getAttribute(GatewayRateLimitConstants.AUTHENTICATED_USER_ID_ATTRIBUTE);
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (userId == null || route == null || !properties.getUserLimitedRoutes().contains(route.getId())) {
            return chain.filter(exchange);
        }

        String resource = GatewayRateLimitConstants.userResource(route.getId());
        Object[] args = {userId};
        try {
            AsyncEntry entry = SphU.asyncEntry(resource, EntryType.IN, 1, args);
            return chain.filter(exchange).doFinally(signalType -> exit(entry, args));
        } catch (BlockException ex) {
            return responseHandler.writeBlockedResponse(exchange, "user", resource, ex);
        }
    }

    @Override
    public int getOrder() {
        return GatewayRateLimitConstants.USER_RATE_LIMIT_FILTER_ORDER;
    }

    private void exit(AsyncEntry entry, Object[] args) {
        try {
            entry.exit(1, args);
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to exit Sentinel async entry for resource {}",
                    entry.getResourceWrapper().getName(), ex);
        }
    }
}
