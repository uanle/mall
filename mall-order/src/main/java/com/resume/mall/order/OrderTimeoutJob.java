package com.resume.mall.order;

import com.resume.mall.common.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
public class OrderTimeoutJob {
    private final JdbcClient jdbcClient;
    private final StringRedisTemplate redisTemplate;

    public OrderTimeoutJob(JdbcClient jdbcClient, StringRedisTemplate redisTemplate) {
        this.jdbcClient = jdbcClient;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${mall.order.timeout-scan-delay:30000}")
    public void closeTimeoutOrders() {
        List<Map<String, Object>> rows = jdbcClient.sql("""
                        select id, activity_id
                        from trade_order
                        where status = 'NEW'
                          and created_at < date_sub(now(3), interval 15 minute)
                        limit 100
                        """)
                .query()
                .listOfRows();

        for (Map<String, Object> row : rows) {
            Long orderId = ((Number) row.get("id")).longValue();
            Long activityId = ((Number) row.get("activity_id")).longValue();
            int updated = jdbcClient.sql("""
                            update trade_order
                            set status = 'CLOSED'
                            where id = ? and status = 'NEW'
                            """)
                    .param(orderId)
                    .update();
            if (updated == 1) {
                redisTemplate.opsForValue().increment(RedisKeys.seckillStock(activityId));
            }
        }
    }
}
