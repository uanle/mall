package com.resume.mall.gateway.ratelimit;

import com.resume.mall.common.UserHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Set;

@Component
public class TrustedHeaderGlobalFilter implements GlobalFilter, Ordered {
    private static final String FORWARDED_HEADER = "Forwarded";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final Set<String> SPOOFABLE_USER_HEADERS = Set.of(
            UserHeaders.USER_ID,
            UserHeaders.USERNAME,
            UserHeaders.USER_ROLE,
            UserHeaders.USER_LEVEL,
            UserHeaders.TOKEN_ID);

    private final GatewayRateLimitProperties properties;

    public TrustedHeaderGlobalFilter(GatewayRateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String remoteAddress = remoteAddress(request.getRemoteAddress());
        String clientIp = resolveClientIp(request.getHeaders(), remoteAddress);

        ServerHttpRequest sanitizedRequest = request.mutate()
                .headers(headers -> {
                    SPOOFABLE_USER_HEADERS.forEach(headers::remove);
                    headers.remove(GatewayRateLimitConstants.REAL_IP_HEADER);
                    headers.remove(X_FORWARDED_FOR_HEADER);
                    headers.remove(FORWARDED_HEADER);
                    headers.set(GatewayRateLimitConstants.REAL_IP_HEADER, clientIp);
                })
                .build();
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }

    @Override
    public int getOrder() {
        return GatewayRateLimitConstants.TRUSTED_HEADER_FILTER_ORDER;
    }

    private String resolveClientIp(HttpHeaders headers, String remoteAddress) {
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = headers.getFirst(X_FORWARDED_FOR_HEADER);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String candidate = forwardedFor.split(",", 2)[0].trim();
        return isIpLiteral(candidate) ? normalizeIpLiteral(candidate) : remoteAddress;
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return properties.getTrustedProxies().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals(remoteAddress.toLowerCase(Locale.ROOT)));
    }

    private String remoteAddress(InetSocketAddress address) {
        if (address == null) {
            return "unknown";
        }
        if (address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }
        return address.getHostString();
    }

    private boolean isIpLiteral(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            return false;
        }
        String normalized = normalizeIpLiteral(value);
        if (normalized.indexOf(':') >= 0) {
            return normalized.matches("[0-9a-fA-F:.%]+");
        }
        String[] octets = normalized.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            try {
                if (octet.isEmpty() || Integer.parseInt(octet) > 255) {
                    return false;
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private String normalizeIpLiteral(String value) {
        String normalized = value.trim().replace("\"", "");
        if (normalized.startsWith("[") && normalized.contains("]")) {
            return normalized.substring(1, normalized.indexOf(']'));
        }
        int colon = normalized.lastIndexOf(':');
        if (colon > 0 && normalized.indexOf(':') == colon && normalized.indexOf('.') > 0) {
            return normalized.substring(0, colon);
        }
        return normalized;
    }
}
