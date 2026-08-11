package com.resume.mall.order.service;

import com.resume.mall.common.RedisKeys;
import com.resume.mall.observability.LogValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TradeOrderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradeOrderService.class);
    private static final String NEW = "NEW";
    private static final String PAID = "PAID";
    private static final String COMPLETED = "COMPLETED";

    private final JdbcClient jdbcClient;
    private final StringRedisTemplate redisTemplate;

    public TradeOrderService(JdbcClient jdbcClient, StringRedisTemplate redisTemplate) {
        this.jdbcClient = jdbcClient;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Map<String, Object> pay(String orderNo, long payerUserId, boolean allowHelpPay) {
        Map<String, Object> order = findByOrderNoOrThrow(orderNo);
        String status = (String) order.get("status");
        if (PAID.equals(status) || COMPLETED.equals(status)) {
            return order;
        }
        if (!NEW.equals(status)) {
            throw new IllegalStateException("seckill order cannot be paid in status " + status);
        }
        long ownerUserId = ((Number) order.get("user_id")).longValue();
        if (!allowHelpPay && ownerUserId != payerUserId) {
            throw new SecurityException("only the order owner can pay this seckill order");
        }

        jdbcClient.sql("""
                        update trade_order
                        set status = 'PAID', payer_user_id = ?, paid_at = ?
                        where order_no = ? and status = 'NEW'
                        """)
                .param(payerUserId)
                .param(LocalDateTime.now())
                .param(orderNo)
                .update();
        cleanupReservationMarkers(order);
        Map<String, Object> paidOrder = findByOrderNoOrThrow(orderNo);
        LOGGER.atInfo()
                .addKeyValue("event", "seckill_order_paid")
                .addKeyValue("orderNo", LogValues.safe(orderNo))
                .addKeyValue("userId", ownerUserId)
                .addKeyValue("payerUserId", payerUserId)
                .addKeyValue("helpPayment", allowHelpPay)
                .log("Seckill order paid");
        return paidOrder;
    }

    @Transactional
    public Map<String, Object> complete(String orderNo) {
        Map<String, Object> order = findByOrderNoOrThrow(orderNo);
        String status = (String) order.get("status");
        if (COMPLETED.equals(status)) {
            return order;
        }
        if (!PAID.equals(status)) {
            throw new IllegalStateException("seckill order cannot be completed in status " + status);
        }

        jdbcClient.sql("""
                        update trade_order
                        set status = 'COMPLETED', completed_at = ?
                        where order_no = ? and status = 'PAID'
                        """)
                .param(LocalDateTime.now())
                .param(orderNo)
                .update();
        Map<String, Object> completedOrder = findByOrderNoOrThrow(orderNo);
        LOGGER.atInfo()
                .addKeyValue("event", "seckill_order_completed")
                .addKeyValue("orderNo", LogValues.safe(orderNo))
                .addKeyValue("userId", order.get("user_id"))
                .addKeyValue("activityId", order.get("activity_id"))
                .log("Seckill order completed");
        return completedOrder;
    }

    public Map<String, Object> findByRequestId(String requestId) {
        List<Map<String, Object>> rows = jdbcClient.sql("""
                        select order_no, user_id, activity_id, product_id, amount_cent, status,
                               request_id, payer_user_id, paid_at, completed_at, created_at, updated_at
                        from trade_order
                        where request_id = ?
                        """)
                .param(requestId)
                .query()
                .listOfRows();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findByOrderNoOrThrow(String orderNo) {
        List<Map<String, Object>> rows = jdbcClient.sql("""
                        select order_no, user_id, activity_id, product_id, amount_cent, status,
                               request_id, payer_user_id, paid_at, completed_at, created_at, updated_at
                        from trade_order
                        where order_no = ?
                        """)
                .param(orderNo)
                .query()
                .listOfRows();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("seckill order not found");
        }
        return rows.get(0);
    }

    private void cleanupReservationMarkers(Map<String, Object> order) {
        long activityId = ((Number) order.get("activity_id")).longValue();
        long userId = ((Number) order.get("user_id")).longValue();
        String requestId = (String) order.get("request_id");
        redisTemplate.delete(RedisKeys.seckillUser(activityId, userId));
        redisTemplate.delete(RedisKeys.seckillRequest(requestId));
    }
}
