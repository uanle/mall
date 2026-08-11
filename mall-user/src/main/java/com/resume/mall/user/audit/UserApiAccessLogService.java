package com.resume.mall.user.audit;

import com.resume.mall.common.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserApiAccessLogService {
    private static final long MAX_PAGE_SIZE = 200;

    private final UserApiAccessLogRepository repository;

    public UserApiAccessLogService(UserApiAccessLogRepository repository) {
        this.repository = repository;
    }

    public PageResult<UserApiAccessLog> page(
            long pageNum,
            long pageSize,
            Long userId,
            String httpMethod,
            String pathLike,
            Integer status,
            Boolean success,
            String traceId,
            String requestId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        long safePageNum = Math.max(pageNum, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long offset = (safePageNum - 1) * safePageSize;
        long total = repository.count(userId, httpMethod, pathLike, status, success, traceId, requestId, createdFrom, createdTo);
        return PageResult.of(safePageNum, safePageSize, total,
                repository.page(offset, safePageSize, userId, httpMethod, pathLike, status, success,
                        traceId, requestId, createdFrom, createdTo));
    }
}
