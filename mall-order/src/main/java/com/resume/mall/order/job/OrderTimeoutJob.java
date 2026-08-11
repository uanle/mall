package com.resume.mall.order.job;

import com.resume.mall.common.RedisKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class OrderTimeoutJob {
    private final JdbcClient jdbcClient;
    private final StringRedisTemplate redisTemplate;
    private final int seckillPaymentTimeoutMinutes;

    public OrderTimeoutJob(
            JdbcClient jdbcClient,
            StringRedisTemplate redisTemplate,
            @Value("${mall.order.seckill-payment-timeout-minutes:15}") int seckillPaymentTimeoutMinutes) {
        this.jdbcClient = jdbcClient;
        this.redisTemplate = redisTemplate;
        this.seckillPaymentTimeoutMinutes = seckillPaymentTimeoutMinutes;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${mall.order.timeout-scan-delay:30000}")
    public void closeTimeoutOrders() {
        List<Map<String, Object>> rows = jdbcClient.sql("""
                        select id, user_id, activity_id, request_id
                        from trade_order
                        where status = 'NEW'
                          and created_at < ?
                        limit 100
                        """)
                .param(LocalDateTime.now().minusMinutes(seckillPaymentTimeoutMinutes))
                .query()
                .listOfRows();

        for (Map<String, Object> row : rows) {
            Long orderId = ((Number) row.get("id")).longValue();
            Long userId = ((Number) row.get("user_id")).longValue();
            Long activityId = ((Number) row.get("activity_id")).longValue();
            String requestId = (String) row.get("request_id");
            int updated = jdbcClient.sql("""
                            update trade_order
                            set status = 'CLOSED'
                            where id = ? and status = 'NEW'
                            """)
                    .param(orderId)
                    .update();
            if (updated == 1) {
                redisTemplate.opsForValue().increment(RedisKeys.seckillStock(activityId));
                redisTemplate.delete(RedisKeys.seckillUser(activityId, userId));
                redisTemplate.delete(RedisKeys.seckillRequest(requestId));
            }
        }
    }
}
