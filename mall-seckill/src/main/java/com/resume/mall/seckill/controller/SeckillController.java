package com.resume.mall.seckill.controller;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.PageResult;
import com.resume.mall.common.ReserveResult;
import com.resume.mall.seckill.dto.CreateSeckillActivityRequest;
import com.resume.mall.seckill.dto.UpdateSeckillActivityRequest;
import com.resume.mall.seckill.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "秒杀服务", description = "秒杀活动、库存预扣、秒杀日志接口")
@RestController
@RequestMapping
public class SeckillController {
    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @Operation(summary = "秒杀资格校验并预扣库存", description = "通过 Redis Lua 原子判断库存、用户是否重复参与、请求是否重复；预扣成功后发布订单创建消息到 RabbitMQ。")
    @PostMapping("/api/seckill/{activityId}/reserve")
    public ApiResponse<ReserveResult> reserve(
            @PathVariable("activityId") long activityId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(seckillService.reserve(activityId, userId, idempotencyKey));
    }

    @Operation(summary = "创建秒杀活动", description = "创建秒杀活动，将活动记录到MySQL数据库以及Redis缓存中.")
    @PostMapping("/internal/seckill/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> createActivity(@Valid @RequestBody CreateSeckillActivityRequest request) {
        return ApiResponse.ok(seckillService.createActivity(request));
    }

    @Operation(summary = "新增秒杀活动")
    @PostMapping("/api/seckill/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> createApiActivity(@Valid @RequestBody CreateSeckillActivityRequest request) {
        return ApiResponse.ok(seckillService.createActivity(request));
    }

//    @Operation(summary = "初始化秒杀 Redis 库存", description = "内部测试接口，用于把指定活动库存写入 Redis。quantity 必须大于等于 0。")
//    @PostMapping("/internal/seckill/{activityId}/stock")
//    @ResponseStatus(HttpStatus.NO_CONTENT)
//    public void initStock(@PathVariable("activityId") long activityId, @RequestParam("quantity") int quantity) {
//        seckillService.initStock(activityId, quantity);
//    }
//
//    @Operation(summary = "Initialize seckill activity Redis stock", description = "Preferred internal path. The Redis key expires at the activity end time.")
//    @PostMapping("/internal/seckill/activities/{activityId}/stock")
//    @ResponseStatus(HttpStatus.NO_CONTENT)
//    public void initActivityStock(@PathVariable("activityId") long activityId, @RequestParam("quantity") int quantity) {
//        seckillService.initStock(activityId, quantity);
//    }

    @Operation(summary = "分页查询秒杀活动", description = "支持按商品 ID、商品名称、活动状态、开始时间区间查询。时间格式：yyyy-MM-dd'T'HH:mm:ss。")
    @GetMapping("/api/seckill/activities")
    public ApiResponse<PageResult<Map<String, Object>>> activities(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "productName", required = false) String productName,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "startFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
            @RequestParam(value = "startTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTo) {
        return ApiResponse.ok(seckillService.pageActivities(
                pageNum, pageSize, productId, productName, status, startFrom, startTo));
    }

    @Operation(summary = "查询秒杀活动详情")
    @GetMapping("/api/seckill/activities/{activityId}")
    public ApiResponse<Map<String, Object>> activity(@PathVariable("activityId") long activityId) {
        return ApiResponse.ok(seckillService.getActivity(activityId));
    }

    @Operation(summary = "修改秒杀活动")
    @PutMapping("/api/seckill/activities/{activityId}")
    public ApiResponse<Map<String, Object>> updateActivity(
            @PathVariable("activityId") long activityId,
            @Valid @RequestBody UpdateSeckillActivityRequest request) {
        return ApiResponse.ok(seckillService.updateActivity(activityId, request));
    }

    @Operation(summary = "删除秒杀活动", description = "软删除：将活动 status 更新为 0，并清理活动 Redis 库存。")
    @DeleteMapping("/api/seckill/activities/{activityId}")
    public ApiResponse<Void> deleteActivity(@PathVariable("activityId") long activityId) {
        seckillService.deleteActivity(activityId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "分页查询秒杀库存扣减日志", description = "支持按用户 ID、活动 ID、处理状态、创建时间区间查询，用于排查重复消费、消息丢失和最终一致性问题。")
    @GetMapping("/api/seckill/stock-deduct-logs")
    public ApiResponse<PageResult<Map<String, Object>>> stockDeductLogs(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "activityId", required = false) Long activityId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        return ApiResponse.ok(seckillService.pageStockDeductLogs(
                pageNum, pageSize, userId, activityId, status, createdFrom, createdTo));
    }
}
