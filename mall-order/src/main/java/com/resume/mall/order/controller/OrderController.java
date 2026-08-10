package com.resume.mall.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.PageResult;
import com.resume.mall.order.dto.CreateOrderRequest;
import com.resume.mall.order.dto.RetailOrderResponse;
import com.resume.mall.order.entity.RetailOrder;
import com.resume.mall.order.mapper.RetailOrderMapper;
import com.resume.mall.order.service.RetailOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "订单服务", description = "普通交易订单、支付模拟、订单查询接口")
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final JdbcClient jdbcClient;
    private final RetailOrderService retailOrderService;
    private final RetailOrderMapper retailOrderMapper;

    public OrderController(JdbcClient jdbcClient, RetailOrderService retailOrderService, RetailOrderMapper retailOrderMapper) {
        this.jdbcClient = jdbcClient;
        this.retailOrderService = retailOrderService;
        this.retailOrderMapper = retailOrderMapper;
    }

    @Operation(summary = "创建普通订单", description = "检查商品和库存，扣减可售库存，生成 CREATED 状态订单；支持 Idempotency-Key 幂等。")
    @PostMapping
    public ApiResponse<RetailOrderResponse> create(
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey;
        return ApiResponse.ok(retailOrderService.create(userId, request, key));
    }

    @Operation(summary = "支付模拟", description = "将 CREATED 状态订单流转为 PAID。")
    @PostMapping("/{orderNo}/payments")
    public ApiResponse<RetailOrderResponse> pay(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.ok(retailOrderService.pay(orderNo));
    }

    @Operation(summary = "完成订单", description = "将 PAID 状态订单流转为 COMPLETED，并确认锁定库存为已售库存。")
    @PostMapping("/{orderNo}/completion")
    public ApiResponse<RetailOrderResponse> complete(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.ok(retailOrderService.complete(orderNo));
    }

    @Operation(summary = "分页查询普通订单", description = "支持按用户 ID、商品 ID、订单状态、创建时间区间查询。时间格式：yyyy-MM-dd'T'HH:mm:ss。")
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> orders(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        return ApiResponse.ok(retailOrderService.pageRetailOrders(
                pageNum, pageSize, userId, productId, status, createdFrom, createdTo));
    }

    @Operation(summary = "查询订单详情", description = "优先按普通订单号查询；未命中时兼容按秒杀 requestId 查询。")
    @GetMapping("/{requestId}")
    public ApiResponse<Map<String, Object>> byRequestId(@PathVariable("requestId") String requestId) {
        RetailOrder retailOrder = retailOrderMapper.selectOne(new LambdaQueryWrapper<RetailOrder>()
                .eq(RetailOrder::getOrderNo, requestId));
        if (retailOrder != null) {
            return ApiResponse.ok(Map.of(
                    "order_no", retailOrder.getOrderNo(),
                    "user_id", retailOrder.getUserId(),
                    "product_id", retailOrder.getProductId(),
                    "quantity", retailOrder.getQuantity(),
                    "amount_cent", retailOrder.getAmountCent(),
                    "status", retailOrder.getStatus(),
                    "created_at", retailOrder.getCreatedAt()));
        }

        List<Map<String, Object>> rows = jdbcClient.sql("""
                        select order_no, user_id, activity_id, product_id, amount_cent, status, created_at
                        from trade_order
                        where request_id = ?
                        """)
                .param(requestId)
                .query()
                .listOfRows();
        if (rows.isEmpty()) {
            return ApiResponse.fail(404, "order not found");
        }
        return ApiResponse.ok(rows.get(0));
    }
}
