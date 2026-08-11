package com.resume.mall.user.audit;

import java.time.LocalDateTime;

public record UserApiAccessLog(
        Long id,
        String traceId,
        String requestId,
        Long userId,
        String userRole,
        String routeId,
        String httpMethod,
        String path,
        int status,
        boolean success,
        long durationMs,
        String clientIp,
        String errorType,
        LocalDateTime createdAt
) {
}
