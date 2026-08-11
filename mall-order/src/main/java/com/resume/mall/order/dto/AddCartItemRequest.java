package com.resume.mall.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull(message = "productId must not be null")
        Long productId,
        @NotNull(message = "quantity must not be null")
        @Min(value = 1, message = "quantity must be greater than 0")
        Integer quantity,
        Boolean selected
) {
}
