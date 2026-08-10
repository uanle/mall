package com.resume.mall.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull(message = "商品 ID 不能为空")
        Long productId,
        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量不能小于 1")
        Integer quantity
) {
}
