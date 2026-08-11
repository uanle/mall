package com.resume.mall.product.dto;

import jakarta.validation.constraints.Min;

public record UpdateInventoryRequest(
        @Min(value = 0, message = "availableStock must be non-negative")
        Integer availableStock,
        @Min(value = 0, message = "lockedStock must be non-negative")
        Integer lockedStock,
        @Min(value = 0, message = "soldStock must be non-negative")
        Integer soldStock
) {
}
