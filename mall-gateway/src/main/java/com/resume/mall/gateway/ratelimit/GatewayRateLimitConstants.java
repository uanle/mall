package com.resume.mall.gateway.ratelimit;

public final class GatewayRateLimitConstants {
    public static final int TRUSTED_HEADER_FILTER_ORDER = -200;
    public static final int AUTH_FILTER_ORDER = -100;
    public static final int USER_RATE_LIMIT_FILTER_ORDER = -90;

    public static final String REAL_IP_HEADER = "X-Real-IP";
    public static final String RATE_LIMIT_RESOURCE_HEADER = "X-RateLimit-Resource";
    public static final String AUTHENTICATED_USER_ID_ATTRIBUTE =
            "com.resume.mall.gateway.authenticatedUserId";
    public static final String AUTHENTICATED_USER_ROLE_ATTRIBUTE =
            "com.resume.mall.gateway.authenticatedUserRole";
    public static final String USER_RESOURCE_PREFIX = "mall-gateway:user:";

    private GatewayRateLimitConstants() {
    }

    public static String userResource(String routeId) {
        return USER_RESOURCE_PREFIX + routeId;
    }
}
