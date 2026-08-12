package com.resume.mall.seckill.service;

import com.resume.mall.common.RedisKeys;
import com.resume.mall.observability.LogValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillPublishCompensationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillPublishCompensationService.class);

    private final JdbcClient jdbcClient;
    private final StringRedisTemplate redisTemplate;

    public SeckillPublishCompensationService(JdbcClient jdbcClient, StringRedisTemplate redisTemplate) {
        this.jdbcClient = jdbcClient;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void markPublished(String requestId) {
        jdbcClient.sql("""
                        update stock_deduct_log
                        set status = 'PUBLISHED', reason = null
                        where request_id = ? and status = 'RESERVED'
                        """)
                .param(requestId)
                .update();
    }

    @Transactional
    public void compensatePublishFailure(String requestId, String reason) {
        Reservation reservation = findCompensatableReservation(requestId);
        if (reservation == null) {
            return;
        }
        int updated = jdbcClient.sql("""
                        update stock_deduct_log
                        set status = 'PUBLISH_FAILED', reason = ?
                        where request_id = ? and status in ('RESERVED', 'PUBLISHED')
                        """)
                .param(reason)
                .param(requestId)
                .update();
        if (updated != 1) {
            return;
        }
        redisTemplate.opsForValue().increment(RedisKeys.seckillStock(reservation.activityId()));
        redisTemplate.delete(RedisKeys.seckillUser(reservation.activityId(), reservation.userId()));
        redisTemplate.delete(RedisKeys.seckillRequest(requestId));
        LOGGER.atWarn()
                .addKeyValue("event", "seckill_publish_compensated")
                .addKeyValue("requestId", LogValues.safe(requestId))
                .addKeyValue("activityId", reservation.activityId())
                .addKeyValue("userId", reservation.userId())
                .addKeyValue("reason", reason)
                .log("Seckill reservation compensated after message publish failure");
    }

    private Reservation findCompensatableReservation(String requestId) {
        return jdbcClient.sql("""
                        select user_id, activity_id
                        from stock_deduct_log
                        where request_id = ?
                          and status in ('RESERVED', 'PUBLISHED')
                        """)
                .param(requestId)
                .query((rs, rowNum) -> new Reservation(
                        rs.getLong("user_id"),
                        rs.getLong("activity_id")))
                .optional()
                .orElse(null);
    }

    private record Reservation(long userId, long activityId) {
    }
}
