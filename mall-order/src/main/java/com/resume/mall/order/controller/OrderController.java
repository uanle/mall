package com.resume.mall.order.controller;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.PageResult;
import com.resume.mall.common.UserHeaders;
import com.resume.mall.order.dto.CreateOrderRequest;
import com.resume.mall.order.dto.RetailOrderResponse;
import com.resume.mall.order.dto.StockCheckResponse;
import com.resume.mall.order.entity.RetailOrder;
import com.resume.mall.order.service.RetailOrderService;
import com.resume.mall.order.service.TradeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Tag(name = "订单服务", description = "普通交易订单、库存检查、支付模拟、订单查询接口")
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final RetailOrderService retailOrderService;
    private final TradeOrderService tradeOrderService;

    public OrderController(RetailOrderService retailOrderService, TradeOrderService tradeOrderService) {
        this.retailOrderService = retailOrderService;
        this.tradeOrderService = tradeOrderService;
    }

    @Operation(summary = "创建普通订单", description = "检查商品和库存，扣减可售库存，生成 CREATED 状态订单；支持 Idempotency-Key 幂等。失败时会返回结构化库存检查结果。")
    @PostMapping
    public ApiResponse<RetailOrderResponse> create(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey;
        return ApiResponse.ok(retailOrderService.create(userId, request, key));
    }

    @Operation(summary = "下单前库存检查", description = "显式查询商品是否可下单，返回商品状态、库存记录状态、可售库存、锁定库存、已售库存和失败原因。")
    @GetMapping("/stock-check")
    public ApiResponse<StockCheckResponse> stockCheck(
            @RequestParam("productId") long productId,
            @RequestParam(value = "quantity", defaultValue = "1") int quantity) {
        return ApiResponse.ok(retailOrderService.checkStock(productId, quantity));
    }

    @Operation(summary = "支付模拟", description = "将 CREATED 状态订单流转为 PAID。")
    @PostMapping("/{orderNo}/payments")
    public ApiResponse<RetailOrderResponse> pay(
            @PathVariable("orderNo") String orderNo,
            @Parameter(hidden = true) @RequestHeader(UserHeaders.USER_ID) long userId) {
        return ApiResponse.ok(retailOrderService.pay(orderNo, userId, false));
    }

    @Operation(summary = "普通订单代付模拟", description = "允许当前登录用户为他人订单支付，并记录实际付款人。")
    @PostMapping("/{orderNo}/help-payments")
    public ApiResponse<RetailOrderResponse> helpPay(
            @PathVariable("orderNo") String orderNo,
            @Parameter(hidden = true) @RequestHeader(UserHeaders.USER_ID) long userId) {
        return ApiResponse.ok(retailOrderService.pay(orderNo, userId, true));
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

    @Operation(summary = "按普通订单号查询订单详情", description = "查询 retail_order 表。参数是 orderNo，不是 requestId。")
    @GetMapping("/{orderNo}")
    public ApiResponse<RetailOrderResponse> byOrderNo(@PathVariable("orderNo") String orderNo) {
        RetailOrder retailOrder = retailOrderService.findByOrderNo(orderNo);
        if (retailOrder == null) {
            return ApiResponse.fail(404, "retail order not found");
        }
        return ApiResponse.ok(RetailOrderResponse.from(retailOrder));
    }

    @Operation(summary = "按秒杀请求 ID 查询订单详情", description = "查询 trade_order 表。这个接口用于秒杀链路，参数是下单请求的 requestId。")
    @GetMapping("/seckill-requests/{requestId}")
    public ApiResponse<Map<String, Object>> bySeckillRequestId(@PathVariable("requestId") String requestId) {
        Map<String, Object> order = tradeOrderService.findByRequestId(requestId);
        if (order == null) {
            return ApiResponse.fail(404, "seckill order not found");
        }
        return ApiResponse.ok(order);
    }

    @Operation(summary = "秒杀订单支付模拟", description = "将 trade_order 的 NEW 状态流转为 PAID。")
    @PostMapping("/seckill-orders/{orderNo}/payments")
    public ApiResponse<Map<String, Object>> paySeckillOrder(
            @PathVariable("orderNo") String orderNo,
            @Parameter(hidden = true) @RequestHeader(UserHeaders.USER_ID) long userId) {
        return ApiResponse.ok(tradeOrderService.pay(orderNo, userId, false));
    }

    @Operation(summary = "秒杀订单代付模拟", description = "允许当前登录用户为他人秒杀订单支付，并记录实际付款人。")
    @PostMapping("/seckill-orders/{orderNo}/help-payments")
    public ApiResponse<Map<String, Object>> helpPaySeckillOrder(
            @PathVariable("orderNo") String orderNo,
            @Parameter(hidden = true) @RequestHeader(UserHeaders.USER_ID) long userId) {
        return ApiResponse.ok(tradeOrderService.pay(orderNo, userId, true));
    }

    @Operation(summary = "完成秒杀订单", description = "将 trade_order 的 PAID 状态流转为 COMPLETED。")
    @PostMapping("/seckill-orders/{orderNo}/completion")
    public ApiResponse<Map<String, Object>> completeSeckillOrder(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.ok(tradeOrderService.complete(orderNo));
    }
}
