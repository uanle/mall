package com.resume.mall.product;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api")
public class ProductController {
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration NULL_TTL = Duration.ofSeconds(30);
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    private final JdbcClient jdbcClient;
    private final StringRedisTemplate redisTemplate;

    public ProductController(JdbcClient jdbcClient, StringRedisTemplate redisTemplate) {
        this.jdbcClient = jdbcClient;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<Map<String, Object>> product(@PathVariable("productId") long productId) {
        String cacheKey = RedisKeys.productCache(productId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (cached.isBlank()) {
                return ApiResponse.fail(404, "product not found");
            }
            return ApiResponse.ok(Map.of("raw", cached, "source", "redis"));
        }

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeys.productMutex(productId), "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return ApiResponse.fail(429, "cache rebuild in progress");
        }

        try {
            Optional<Map<String, Object>> row = jdbcClient.sql("""
                            select id, name, price_cent, status
                            from product
                            where id = ? and status = 1
                            """)
                    .param(productId)
                    .query()
                    .listOfRows()
                    .stream()
                    .findFirst();
            if (row.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, "", NULL_TTL);
                return ApiResponse.fail(404, "product not found");
            }
            String value = row.get().toString();
            redisTemplate.opsForValue().set(cacheKey, value, jitter(CACHE_TTL));
            return ApiResponse.ok(Map.of("raw", value, "source", "mysql"));
        } finally {
            redisTemplate.delete(RedisKeys.productMutex(productId));
        }
    }

    @GetMapping("/activities/{activityId}")
    public ApiResponse<Map<String, Object>> activity(@PathVariable("activityId") long activityId) {
        List<Map<String, Object>> rows = jdbcClient.sql("""
                        select a.id, a.product_id, p.name, p.price_cent, a.total_stock, a.start_time, a.end_time, a.status
                        from seckill_activity a
                        join product p on p.id = a.product_id
                        where a.id = ? and a.status = 1
                        """)
                .param(activityId)
                .query()
                .listOfRows();
        if (rows.isEmpty()) {
            return ApiResponse.fail(404, "activity not found");
        }
        Map<String, Object> row = rows.get(0);
        return ApiResponse.ok(row);
    }

    private Duration jitter(Duration base) {
        return base.plusSeconds(ThreadLocalRandom.current().nextLong(30, 120));
    }
}
