package com.resume.mall.product.dto;

import jakarta.validation.constraints.Min;

public record UpdateProductRequest(
        String name,
        @Min(value = 1, message = "priceCent must be greater than 0")
        Long priceCent,
        Integer status
) {
}
