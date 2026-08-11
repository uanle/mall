package com.resume.mall.gateway.audit;

import java.time.LocalDateTime;
import java.util.List;

public interface UserApiAccessLogRepository {
    void save(UserApiAccessLog log);

    long count(
            Long userId,
            String httpMethod,
            String pathLike,
            Integer status,
            Boolean success,
            String traceId,
            String requestId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo);

    List<UserApiAccessLog> page(
            long offset,
            long pageSize,
            Long userId,
            String httpMethod,
            String pathLike,
            Integer status,
            Boolean success,
            String traceId,
            String requestId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo);
}
