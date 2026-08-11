package com.resume.mall.product.controller;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.PageResult;
import com.resume.mall.product.dto.CreateInventoryRequest;
import com.resume.mall.product.dto.CreateProductRequest;
import com.resume.mall.product.dto.UpdateInventoryRequest;
import com.resume.mall.product.dto.UpdateProductRequest;
import com.resume.mall.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "商品服务", description = "商品、库存和秒杀活动查询接口")
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

    @Operation(
            summary = "分页查询商品",
            description = "支持按商品名称、上下架状态查询。sort 支持 relevance、latest、priceAsc、priceDesc、idAsc；默认 relevance，为后续接入 Elasticsearch 的相关性排序预留。pageSize 最大 100。")
    @GetMapping("/products")
    public ApiResponse<PageResult<Map<String, Object>>> products(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "sort", defaultValue = "relevance") String sort) {
        return ApiResponse.ok(productService.pageProducts(pageNum, pageSize, name, status, sort));
    }

    @Operation(summary = "新增商品")
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.ok(productService.createProduct(request));
    }

    @Operation(summary = "修改商品")
    @PutMapping("/products/{productId}")
    public ApiResponse<Map<String, Object>> updateProduct(
            @PathVariable("productId") long productId,
            @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(productService.updateProduct(productId, request));
    }

    @Operation(summary = "删除商品", description = "软删除：将商品 status 更新为 0，并清理商品详情缓存。")
    @DeleteMapping("/products/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable("productId") long productId) {
        productService.deleteProduct(productId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "查询秒杀活动详情")
    @GetMapping("/activities/{activityId}")
    public ApiResponse<Map<String, Object>> activity(@PathVariable("activityId") long activityId) {
        return ApiResponse.ok(productService.getActivityDetail(activityId));
    }

    @Operation(summary = "分页查询秒杀活动", description = "支持按商品名称、活动状态、开始时间区间、结束时间区间查询。时间格式：yyyy-MM-dd'T'HH:mm:ss。")
    @GetMapping("/activities")
    public ApiResponse<PageResult<Map<String, Object>>> activities(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "productName", required = false) String productName,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "startFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
            @RequestParam(value = "startTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTo,
            @RequestParam(value = "endFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endFrom,
            @RequestParam(value = "endTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTo) {
        return ApiResponse.ok(productService.pageActivities(
                pageNum, pageSize, productName, status, startFrom, startTo, endFrom, endTo));
    }

    @Operation(summary = "分页查询商品库存", description = "支持按商品名称、可售库存上下限查询，用于后台库存排查和低库存预警。")
    @GetMapping("/inventories")
    public ApiResponse<PageResult<Map<String, Object>>> inventories(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "productName", required = false) String productName,
            @RequestParam(value = "availableGte", required = false) Integer availableGte,
            @RequestParam(value = "availableLte", required = false) Integer availableLte) {
        return ApiResponse.ok(productService.pageInventories(
                pageNum, pageSize, productName, availableGte, availableLte));
    }

    @Operation(summary = "查询商品库存详情")
    @GetMapping("/inventories/{productId}")
    public ApiResponse<Map<String, Object>> inventory(@PathVariable("productId") long productId) {
        return ApiResponse.ok(productService.getInventory(productId));
    }

    @Operation(summary = "新增商品库存")
    @PostMapping("/inventories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> createInventory(@Valid @RequestBody CreateInventoryRequest request) {
        return ApiResponse.ok(productService.createInventory(request));
    }

    @Operation(summary = "修改商品库存")
    @PutMapping("/inventories/{productId}")
    public ApiResponse<Map<String, Object>> updateInventory(
            @PathVariable("productId") long productId,
            @Valid @RequestBody UpdateInventoryRequest request) {
        return ApiResponse.ok(productService.updateInventory(productId, request));
    }

    @Operation(summary = "删除商品库存")
    @DeleteMapping("/inventories/{productId}")
    public ApiResponse<Void> deleteInventory(@PathVariable("productId") long productId) {
        productService.deleteInventory(productId);
        return ApiResponse.ok(null);
    }
}
