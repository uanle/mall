package com.resume.mall.user.audit;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.PageResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit/access-logs")
public class UserApiAccessLogController {
    private final UserApiAccessLogService service;

    public UserApiAccessLogController(UserApiAccessLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<UserApiAccessLog>> page(
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "requestId", required = false) String requestId,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        return ApiResponse.ok(service.page(pageNum, pageSize, userId, method, path, status, success,
                traceId, requestId, createdFrom, createdTo));
    }
}
