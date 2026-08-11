package com.resume.mall.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateProductRequest(
        Long id,
        @NotBlank(message = "name must not be blank")
        String name,
        @Min(value = 1, message = "priceCent must be greater than 0")
        Long priceCent,
        Integer status
) {
}
