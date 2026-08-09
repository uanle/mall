package com.resume.mall.seckill.controller;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.ReserveResult;
import com.resume.mall.seckill.service.SeckillService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class SeckillController {
    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping("/api/seckill/{activityId}/reserve")
    public ApiResponse<ReserveResult> reserve(
            @PathVariable("activityId") long activityId,
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(seckillService.reserve(activityId, userId, idempotencyKey));
    }

    @PostMapping("/internal/seckill/{activityId}/stock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void initStock(@PathVariable("activityId") long activityId, @RequestParam("quantity") int quantity) {
        seckillService.initStock(activityId, quantity);
    }
}
