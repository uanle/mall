package com.resume.mall.gateway.filter;

import com.resume.mall.common.JwtClaims;
import com.resume.mall.common.JwtUtil;
import com.resume.mall.common.RedisKeys;
import com.resume.mall.common.UserHeaders;
import com.resume.mall.gateway.ratelimit.GatewayRateLimitConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private final String jwtSecret;
    private final ReactiveStringRedisTemplate redisTemplate;

    public AuthGlobalFilter(
            @Value("${mall.jwt.secret}") String jwtSecret,
            ReactiveStringRedisTemplate redisTemplate) {
        this.jwtSecret = jwtSecret;
        this.redisTemplate = redisTemplate;
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
            claims = parseToken(
                    request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION),
                    request.getHeaders().getFirst("accessToken"));
        } catch (IllegalArgumentException ex) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, ex.getMessage());
        }

        JwtClaims finalClaims = claims;
        return redisTemplate.hasKey(RedisKeys.tokenSession(claims.jti()))
                .flatMap(active -> {
                    if (!Boolean.TRUE.equals(active)) {
                        return writeError(exchange, HttpStatus.UNAUTHORIZED, "token session expired or logged out");
                    }
                    if (requiresAdmin(path, request.getMethod()) && !"ADMIN".equals(finalClaims.role())) {
                        return writeError(exchange, HttpStatus.FORBIDDEN, "admin permission required");
                    }

                    ServerHttpRequest mutatedRequest = request.mutate()
                            .headers(headers -> {
                                headers.remove(UserHeaders.USER_ID);
                                headers.remove(UserHeaders.USERNAME);
                                headers.remove(UserHeaders.USER_ROLE);
                                headers.remove(UserHeaders.USER_LEVEL);
                                headers.remove(UserHeaders.TOKEN_ID);
                            })
                            .header(UserHeaders.USER_ID, String.valueOf(finalClaims.userId()))
                            .header(UserHeaders.USERNAME, finalClaims.username())
                            .header(UserHeaders.USER_ROLE, finalClaims.role())
                            .header(UserHeaders.USER_LEVEL, finalClaims.level())
                            .header(UserHeaders.TOKEN_ID, finalClaims.jti())
                            .build();
                    ServerWebExchange authenticatedExchange = exchange.mutate().request(mutatedRequest).build();
                    authenticatedExchange.getAttributes().put(
                            GatewayRateLimitConstants.AUTHENTICATED_USER_ID_ATTRIBUTE, finalClaims.userId());
                    authenticatedExchange.getAttributes().put(
                            GatewayRateLimitConstants.AUTHENTICATED_USER_ROLE_ATTRIBUTE, finalClaims.role());
                    return chain.filter(authenticatedExchange);
                });
    }

    @Override
    public int getOrder() {
        return GatewayRateLimitConstants.AUTH_FILTER_ORDER;
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (path.equals("/api/auth/register") || path.equals("/api/auth/login")) {
            return true;
        }
        if (path.startsWith("/swagger-ui/") || path.startsWith("/v3/api-docs")) {
            return true;
        }
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) {
            return true;
        }
        if (HttpMethod.GET.equals(method)
                && (path.startsWith("/api/products") || path.startsWith("/api/activities"))) {
            return true;
        }
        return false;
    }

    private boolean requiresAdmin(String path, HttpMethod method) {
        if (path.startsWith("/actuator/")) {
            return true;
        }
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

    private JwtClaims parseToken(String authorization, String accessToken) {
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring("Bearer ".length()).trim();
        } else if (accessToken != null && !accessToken.isBlank()) {
            String value = accessToken.trim();
            token = value.startsWith("Bearer ") ? value.substring("Bearer ".length()).trim() : value;
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("missing Authorization or accessToken header");
        }
        return JwtUtil.parseAndValidate(token, jwtSecret);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + status.value()
                + ",\"message\":\"" + escapeJson(message) + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
