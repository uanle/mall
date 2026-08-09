package com.resume.mall.seckill;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.OrderCreateMessage;
import com.resume.mall.common.RabbitNames;
import com.resume.mall.common.RedisKeys;
import com.resume.mall.common.ReserveResult;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class SeckillController {
    private static final long PRODUCT_ID = 2001L;
    private static final long AMOUNT_CENT = 199900L;
    private static final Duration IDEMPOTENT_TTL = Duration.ofHours(2);

    private static final String RESERVE_STOCK_LUA = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock <= 0 then
                return 0
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 2
            end
            if redis.call('EXISTS', KEYS[3]) == 1 then
                return 3
            end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3])
            redis.call('SET', KEYS[3], 'RESERVED', 'EX', ARGV[3])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final DefaultRedisScript<Long> reserveScript;

    public SeckillController(StringRedisTemplate redisTemplate, RabbitTemplate rabbitTemplate) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.reserveScript = new DefaultRedisScript<>(RESERVE_STOCK_LUA, Long.class);
    }

    @PostMapping("/api/seckill/{activityId}/reserve")
    public ApiResponse<ReserveResult> reserve(
            @PathVariable("activityId") long activityId,
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String requestId = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey;

        List<String> keys = List.of(
                RedisKeys.seckillStock(activityId),
                RedisKeys.seckillUser(activityId, userId),
                RedisKeys.seckillRequest(requestId));
        Long result = redisTemplate.execute(
                reserveScript,
                keys,
                String.valueOf(userId),
                requestId,
                String.valueOf(IDEMPOTENT_TTL.toSeconds()));

        if (result == null || result == 0L) {
            return ApiResponse.fail(409, "stock sold out");
        }
        if (result == 2L) {
            return ApiResponse.fail(409, "user already reserved this activity");
        }
        if (result == 3L) {
            return ApiResponse.ok(new ReserveResult(requestId, "DUPLICATE", "request already reserved"));
        }

        OrderCreateMessage message = new OrderCreateMessage(
                requestId,
                userId,
                activityId,
                PRODUCT_ID,
                AMOUNT_CENT,
                Instant.now());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitNames.ORDER_EXCHANGE,
                    RabbitNames.ORDER_CREATE_ROUTING_KEY,
                    message,
                    new CorrelationData(requestId));
        } catch (RuntimeException ex) {
            redisTemplate.opsForValue().increment(RedisKeys.seckillStock(activityId));
            redisTemplate.delete(RedisKeys.seckillUser(activityId, userId));
            redisTemplate.delete(RedisKeys.seckillRequest(requestId));
            throw ex;
        }
        return ApiResponse.ok(new ReserveResult(requestId, "RESERVED", "order event published"));
    }

    @PostMapping("/internal/seckill/{activityId}/stock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void initStock(@PathVariable("activityId") long activityId, @RequestParam("quantity") int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be non-negative");
        }
        redisTemplate.opsForValue().set(RedisKeys.seckillStock(activityId), String.valueOf(quantity));
    }
}
