package com.resume.mall.product.controller;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Products", description = "商品查询接口")
@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "查询商品详情")
    @GetMapping("/products/{productId}")
    public ApiResponse<Map<String, Object>> product(@PathVariable("productId") long productId) {
        return ApiResponse.ok(productService.getProductDetail(productId));
    }

    @Operation(summary = "查询秒杀活动详情")
    @GetMapping("/activities/{activityId}")
    public ApiResponse<Map<String, Object>> activity(@PathVariable("activityId") long activityId) {
        return ApiResponse.ok(productService.getActivityDetail(activityId));
    }
}
