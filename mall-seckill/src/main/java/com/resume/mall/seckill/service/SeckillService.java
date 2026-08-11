package com.resume.mall.seckill.service;

import com.resume.mall.common.PageResult;
import com.resume.mall.common.OrderCreateMessage;
import com.resume.mall.common.RabbitNames;
import com.resume.mall.common.RedisKeys;
import com.resume.mall.common.ReserveResult;
import com.resume.mall.observability.LogValues;
import com.resume.mall.seckill.dto.CreateSeckillActivityRequest;
import com.resume.mall.seckill.dto.UpdateSeckillActivityRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SeckillService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillService.class);
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
    private final JdbcClient jdbcClient;
    private final DefaultRedisScript<Long> reserveScript;

    public SeckillService(StringRedisTemplate redisTemplate, RabbitTemplate rabbitTemplate, JdbcClient jdbcClient) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.jdbcClient = jdbcClient;
        this.reserveScript = new DefaultRedisScript<>(RESERVE_STOCK_LUA, Long.class);
    }

    public ReserveResult reserve(long activityId, long userId, String idempotencyKey) {
        ActivitySnapshot activity = findActiveActivity(activityId);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.startTime())) {
            throw new IllegalStateException("activity has not started");
        }
        if (!now.isBefore(activity.endTime())) {
            throw new IllegalStateException("activity has ended");
        }

        String requestId = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey;
        if (requestAlreadyAccepted(requestId)) {
            LOGGER.atInfo()
                    .addKeyValue("event", "seckill_reservation_duplicate")
                    .addKeyValue("requestId", LogValues.safe(requestId))
                    .addKeyValue("activityId", activityId)
                    .addKeyValue("userId", userId)
                    .log("Seckill request was already accepted");
            return new ReserveResult(requestId, "DUPLICATE", "request already accepted");
        }
        if (userHasUnclosedOrder(userId, activityId)) {
            throw new IllegalStateException("user already reserved this activity");
        }

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
            throw new IllegalStateException("stock sold out");
        }
        if (result == 2L) {
            throw new IllegalStateException("user already reserved this activity");
        }
        if (result == 3L) {
            LOGGER.atInfo()
                    .addKeyValue("event", "seckill_reservation_duplicate")
                    .addKeyValue("requestId", LogValues.safe(requestId))
                    .addKeyValue("activityId", activityId)
                    .addKeyValue("userId", userId)
                    .log("Seckill request was already reserved");
            return new ReserveResult(requestId, "DUPLICATE", "request already reserved");
        }

        OrderCreateMessage message = new OrderCreateMessage(
                requestId,
                userId,
                activityId,
                activity.productId(),
                activity.amountCent(),
                Instant.now());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitNames.ORDER_EXCHANGE,
                    RabbitNames.ORDER_CREATE_ROUTING_KEY,
                    message,
                    new CorrelationData(requestId));
        } catch (RuntimeException ex) {
            LOGGER.atError()
                    .addKeyValue("event", "order_message_publish_failed")
                    .addKeyValue("requestId", LogValues.safe(requestId))
                    .addKeyValue("activityId", activityId)
                    .addKeyValue("userId", userId)
                    .setCause(ex)
                    .log("Failed to publish order creation message; reservation will be rolled back");
            redisTemplate.opsForValue().increment(RedisKeys.seckillStock(activityId));
            redisTemplate.delete(RedisKeys.seckillUser(activityId, userId));
            redisTemplate.delete(RedisKeys.seckillRequest(requestId));
            throw ex;
        }
        LOGGER.atInfo()
                .addKeyValue("event", "seckill_reservation_accepted")
                .addKeyValue("requestId", LogValues.safe(requestId))
                .addKeyValue("activityId", activityId)
                .addKeyValue("productId", activity.productId())
                .addKeyValue("userId", userId)
                .log("Seckill reservation accepted and order event published");
        return new ReserveResult(requestId, "RESERVED", "order event published");
    }

    public Map<String, Object> createActivity(CreateSeckillActivityRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        if (!LocalDateTime.now().isBefore(request.endTime())) {
            throw new IllegalArgumentException("endTime must be in the future");
        }
        int status = request.status() == null ? 1 : request.status();
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
        ensureActiveProductExists(request.productId());

        long activityId = request.id() == null ? generateActivityId() : request.id();
        try {
            jdbcClient.sql("""
                            insert into seckill_activity(id, product_id, start_time, end_time, total_stock, status)
                            values (?, ?, ?, ?, ?, ?)
                            """)
                    .param(activityId)
                    .param(request.productId())
                    .param(request.startTime())
                    .param(request.endTime())
                    .param(request.totalStock())
                    .param(status)
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("activity id already exists");
        }

        if (status == 1) {
            initStock(activityId, request.totalStock());
        }
        LOGGER.atInfo()
                .addKeyValue("event", "seckill_activity_created")
                .addKeyValue("activityId", activityId)
                .addKeyValue("productId", request.productId())
                .addKeyValue("totalStock", request.totalStock())
                .addKeyValue("status", status)
                .log("Seckill activity created");
        return getActivity(activityId);
    }

    public Map<String, Object> updateActivity(long activityId, UpdateSeckillActivityRequest request) {
        ActivityDetail current = findActivityDetail(activityId);
        long productId = request.productId() == null ? current.productId() : request.productId();
        LocalDateTime startTime = request.startTime() == null ? current.startTime() : request.startTime();
        LocalDateTime endTime = request.endTime() == null ? current.endTime() : request.endTime();
        int totalStock = request.totalStock() == null ? current.totalStock() : request.totalStock();
        int status = request.status() == null ? current.status() : request.status();

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        if (totalStock < 1) {
            throw new IllegalArgumentException("totalStock must be greater than 0");
        }
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
        ensureActiveProductExists(productId);

        jdbcClient.sql("""
                        update seckill_activity
                        set product_id = ?, start_time = ?, end_time = ?, total_stock = ?, status = ?
                        where id = ?
                        """)
                .param(productId)
                .param(startTime)
                .param(endTime)
                .param(totalStock)
                .param(status)
                .param(activityId)
                .update();
        if (status == 1 && LocalDateTime.now().isBefore(endTime)) {
            initStock(activityId, totalStock);
        } else {
            redisTemplate.delete(RedisKeys.seckillStock(activityId));
        }
        LOGGER.atInfo()
                .addKeyValue("event", "seckill_activity_updated")
                .addKeyValue("activityId", activityId)
                .addKeyValue("productId", productId)
                .addKeyValue("totalStock", totalStock)
                .addKeyValue("status", status)
                .log("Seckill activity updated");
        return getActivity(activityId);
    }

    public void deleteActivity(long activityId) {
        findActivityDetail(activityId);
        jdbcClient.sql("update seckill_activity set status = 0 where id = ?")
                .param(activityId)
                .update();
        redisTemplate.delete(RedisKeys.seckillStock(activityId));
        LOGGER.atInfo()
                .addKeyValue("event", "seckill_activity_disabled")
                .addKeyValue("activityId", activityId)
                .log("Seckill activity disabled");
    }

    public void initStock(long activityId, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be non-negative");
        }
        ActivitySnapshot activity = findActiveActivity(activityId);
        if (quantity > activity.totalStock()) {
            throw new IllegalArgumentException("quantity must not exceed activity total stock");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(activity.endTime())) {
            throw new IllegalStateException("activity has ended");
        }
        Duration ttl = Duration.between(now, activity.endTime());
        redisTemplate.opsForValue().set(RedisKeys.seckillStock(activityId), String.valueOf(quantity), ttl);
        LOGGER.atInfo()
                .addKeyValue("event", "seckill_stock_initialized")
                .addKeyValue("activityId", activityId)
                .addKeyValue("quantity", quantity)
                .addKeyValue("ttlSeconds", ttl.toSeconds())
                .log("Seckill stock initialized");
    }

    public Map<String, Object> getActivity(long activityId) {
        List<Map<String, Object>> rows = queryRows("""
                        select a.id, a.product_id, p.name as product_name, p.price_cent,
                               a.total_stock, a.start_time, a.end_time, a.status,
                               a.created_at, a.updated_at
                        from seckill_activity a
                        join product p on p.id = a.product_id
                        where a.id = ?
                        """,
                List.of(activityId));
        if (rows.isEmpty()) {
            throw new NoSuchElementException("activity not found");
        }
        return rows.get(0);
    }

    public PageResult<Map<String, Object>> pageActivities(
            int pageNum,
            int pageSize,
            Long productId,
            String productName,
            Integer status,
            LocalDateTime startFrom,
            LocalDateTime startTo) {
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (productId != null) {
            where.append(" and a.product_id = ?");
            params.add(productId);
        }
        if (productName != null && !productName.isBlank()) {
            where.append(" and p.name like ?");
            params.add("%" + productName.trim() + "%");
        }
        if (status != null) {
            where.append(" and a.status = ?");
            params.add(status);
        }
        if (startFrom != null) {
            where.append(" and a.start_time >= ?");
            params.add(startFrom);
        }
        if (startTo != null) {
            where.append(" and a.start_time <= ?");
            params.add(startTo);
        }

        String from = """
                from seckill_activity a
                join product p on p.id = a.product_id
                """;
        long total = queryCount("select count(*) " + from + where, params);
        if (total == 0) {
            throw new NoSuchElementException("data not found");
        }

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(normalizedPageSize);
        dataParams.add(offset);
        List<Map<String, Object>> records = queryRows("""
                        select a.id, a.product_id, p.name as product_name, p.price_cent,
                               a.total_stock, a.start_time, a.end_time, a.status,
                               a.created_at, a.updated_at
                        """ + from + where + " order by a.start_time asc, a.id asc limit ? offset ?",
                dataParams);
        return PageResult.of(normalizedPageNum, normalizedPageSize, total, records);
    }

    public PageResult<Map<String, Object>> pageStockDeductLogs(
            int pageNum,
            int pageSize,
            Long userId,
            Long activityId,
            String status,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            where.append(" and user_id = ?");
            params.add(userId);
        }
        if (activityId != null) {
            where.append(" and activity_id = ?");
            params.add(activityId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" and status = ?");
            params.add(status.trim());
        }
        if (createdFrom != null) {
            where.append(" and created_at >= ?");
            params.add(createdFrom);
        }
        if (createdTo != null) {
            where.append(" and created_at <= ?");
            params.add(createdTo);
        }

        long total = queryCount("select count(*) from stock_deduct_log" + where, params);
        if (total == 0) {
            throw new NoSuchElementException("data not found");
        }

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(normalizedPageSize);
        dataParams.add(offset);
        List<Map<String, Object>> records = queryRows("""
                        select id, request_id, user_id, activity_id, status, reason, created_at
                        from stock_deduct_log
                        """ + where + " order by created_at desc, id desc limit ? offset ?",
                dataParams);
        return PageResult.of(normalizedPageNum, normalizedPageSize, total, records);
    }

    private int normalizePageNum(int pageNum) {
        return Math.max(pageNum, 1);
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private long queryCount(String sql, List<Object> params) {
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql);
        for (Object param : params) {
            spec = spec.param(param);
        }
        return spec.query(Long.class).single();
    }

    private List<Map<String, Object>> queryRows(String sql, List<Object> params) {
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql);
        for (Object param : params) {
            spec = spec.param(param);
        }
        return spec.query().listOfRows();
    }

    private ActivitySnapshot findActiveActivity(long activityId) {
        return jdbcClient.sql("""
                        select a.product_id, p.price_cent, a.total_stock, a.start_time, a.end_time
                        from seckill_activity a
                        join product p on p.id = a.product_id
                        where a.id = ?
                          and a.status = 1
                          and p.status = 1
                        """)
                .param(activityId)
                .query((rs, rowNum) -> new ActivitySnapshot(
                        rs.getLong("product_id"),
                        rs.getLong("price_cent"),
                        rs.getInt("total_stock"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime()))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("active activity not found"));
    }

    private void ensureActiveProductExists(long productId) {
        long count = jdbcClient.sql("select count(*) from product where id = ? and status = 1")
                .param(productId)
                .query(Long.class)
                .single();
        if (count == 0) {
            throw new IllegalArgumentException("active product not found");
        }
    }

    private long generateActivityId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private boolean requestAlreadyAccepted(String requestId) {
        long count = jdbcClient.sql("select count(*) from trade_order where request_id = ?")
                .param(requestId)
                .query(Long.class)
                .single();
        return count > 0;
    }

    private boolean userHasUnclosedOrder(long userId, long activityId) {
        long count = jdbcClient.sql("""
                        select count(*)
                        from trade_order
                        where user_id = ?
                          and activity_id = ?
                          and status in ('NEW', 'PAID', 'COMPLETED')
                        """)
                .param(userId)
                .param(activityId)
                .query(Long.class)
                .single();
        return count > 0;
    }

    private ActivityDetail findActivityDetail(long activityId) {
        return jdbcClient.sql("""
                        select product_id, total_stock, start_time, end_time, status
                        from seckill_activity
                        where id = ?
                        """)
                .param(activityId)
                .query((rs, rowNum) -> new ActivityDetail(
                        rs.getLong("product_id"),
                        rs.getInt("total_stock"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getInt("status")))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("activity not found"));
    }

    private record ActivitySnapshot(
            long productId,
            long amountCent,
            int totalStock,
            LocalDateTime startTime,
            LocalDateTime endTime) {
    }

    private record ActivityDetail(
            long productId,
            int totalStock,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int status) {
    }
}
