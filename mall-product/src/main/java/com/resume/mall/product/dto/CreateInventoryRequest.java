package com.resume.mall.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateInventoryRequest(
        @NotNull(message = "productId must not be null")
        Long productId,
        @NotNull(message = "availableStock must not be null")
        @Min(value = 0, message = "availableStock must be non-negative")
        Integer availableStock,
        @Min(value = 0, message = "lockedStock must be non-negative")
        Integer lockedStock,
        @Min(value = 0, message = "soldStock must be non-negative")
        Integer soldStock
) {
}
