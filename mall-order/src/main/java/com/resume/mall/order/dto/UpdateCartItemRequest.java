package com.resume.mall.order.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
        @Min(value = 1, message = "quantity must be greater than 0")
        Integer quantity,
        Boolean selected
) {
}
