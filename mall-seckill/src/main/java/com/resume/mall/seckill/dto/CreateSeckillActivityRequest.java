package com.resume.mall.seckill.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateSeckillActivityRequest(
        Long id,
        @NotNull(message = "productId must not be null")
        Long productId,
        @NotNull(message = "startTime must not be null")
        LocalDateTime startTime,
        @NotNull(message = "endTime must not be null")
        LocalDateTime endTime,
        @NotNull(message = "totalStock must not be null")
        @Min(value = 1, message = "totalStock must be greater than 0")
        Integer totalStock,
        Integer status
) {
}
