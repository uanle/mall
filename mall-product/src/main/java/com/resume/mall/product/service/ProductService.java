package com.resume.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resume.mall.common.RedisKeys;
import com.resume.mall.product.entity.Product;
import com.resume.mall.product.mapper.ProductMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration NULL_TTL = Duration.ofSeconds(30);
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    private final JdbcClient jdbcClient;
    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;

    public ProductService(JdbcClient jdbcClient, StringRedisTemplate redisTemplate, ProductMapper productMapper) {
        this.jdbcClient = jdbcClient;
        this.redisTemplate = redisTemplate;
        this.productMapper = productMapper;
    }

    public Map<String, Object> getProductDetail(long productId) {
        String cacheKey = RedisKeys.productCache(productId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (cached.isBlank()) {
                throw new IllegalArgumentException("product not found");
            }
            return Map.of("raw", cached, "source", "redis");
        }

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeys.productMutex(productId), "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new IllegalStateException("cache rebuild in progress");
        }

        try {
            Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                    .eq(Product::getId, productId)
                    .eq(Product::getStatus, 1));
            if (product == null) {
                redisTemplate.opsForValue().set(cacheKey, "", NULL_TTL);
                throw new IllegalArgumentException("product not found");
            }
            String value = Map.of(
                    "id", product.getId(),
                    "name", product.getName(),
                    "price_cent", product.getPriceCent(),
                    "status", product.getStatus()).toString();
            redisTemplate.opsForValue().set(cacheKey, value, jitter(CACHE_TTL));
            return Map.of("raw", value, "source", "mysql");
        } finally {
            redisTemplate.delete(RedisKeys.productMutex(productId));
        }
    }

    public Map<String, Object> getActivityDetail(long activityId) {
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
            throw new IllegalArgumentException("activity not found");
        }
        return rows.get(0);
    }

    private Duration jitter(Duration base) {
        return base.plusSeconds(ThreadLocalRandom.current().nextLong(30, 120));
    }
}
