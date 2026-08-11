package com.resume.mall.seckill.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

public record UpdateSeckillActivityRequest(
        Long productId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        @Min(value = 1, message = "totalStock must be greater than 0")
        Integer totalStock,
        Integer status
) {
}
