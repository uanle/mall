package com.resume.mall.gateway.filter;

import com.resume.mall.common.JwtClaims;
import com.resume.mall.common.JwtUtil;
import com.resume.mall.common.UserHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private final String jwtSecret;

    public AuthGlobalFilter(@Value("${mall.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublic(path, request.getMethod())) {
            return chain.filter(exchange);
        }

        JwtClaims claims;
        try {
            claims = parseBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        } catch (IllegalArgumentException ex) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (requiresAdmin(path, request.getMethod()) && !"ADMIN".equals(claims.role())) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove(UserHeaders.USER_ID);
                    headers.remove(UserHeaders.USERNAME);
                    headers.remove(UserHeaders.USER_ROLE);
                    headers.remove(UserHeaders.USER_LEVEL);
                })
                .header(UserHeaders.USER_ID, String.valueOf(claims.userId()))
                .header(UserHeaders.USERNAME, claims.username())
                .header(UserHeaders.USER_ROLE, claims.role())
                .header(UserHeaders.USER_LEVEL, claims.level())
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (path.startsWith("/swagger-ui/") || path.startsWith("/v3/api-docs") || path.startsWith("/actuator/")) {
            return true;
        }
        if (HttpMethod.GET.equals(method)
                && (path.startsWith("/api/products") || path.startsWith("/api/activities"))) {
            return true;
        }
        return false;
    }

    private boolean requiresAdmin(String path, HttpMethod method) {
        if (path.startsWith("/internal/")) {
            return true;
        }
        if (path.startsWith("/api/inventories")) {
            return true;
        }
        if (path.equals("/api/users")) {
            return true;
        }
        if (path.startsWith("/api/users/") && !path.equals("/api/users/me")) {
            return true;
        }
        if (path.equals("/api/orders") && HttpMethod.GET.equals(method)) {
            return true;
        }
        if (path.startsWith("/api/seckill/stock-deduct-logs")) {
            return true;
        }
        return false;
    }

    private JwtClaims parseBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("missing bearer token");
        }
        return JwtUtil.parseAndValidate(authorization.substring("Bearer ".length()), jwtSecret);
    }
}
