package com.resume.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resume.mall.common.PageResult;
import com.resume.mall.common.RedisKeys;
import com.resume.mall.product.entity.Product;
import com.resume.mall.product.mapper.ProductMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

    public PageResult<Map<String, Object>> pageProducts(
            int pageNum,
            int pageSize,
            String name,
            Integer status,
            String sort) {
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            where.append(" and name like ?");
            params.add("%" + name.trim() + "%");
        }
        if (status != null) {
            where.append(" and status = ?");
            params.add(status);
        }

        long total = queryCount("select count(*) from product" + where, params);
        if (total == 0) {
            throw new NoSuchElementException("data not found");
        }

        List<Object> dataParams = new ArrayList<>(params);
        String orderBy = productOrderBy(sort, name, dataParams);
        dataParams.add(normalizedPageSize);
        dataParams.add(offset);
        List<Map<String, Object>> records = queryRows("""
                        select id, name, price_cent, status, created_at, updated_at
                        from product
                        """ + where + orderBy + " limit ? offset ?",
                dataParams);
        return PageResult.of(normalizedPageNum, normalizedPageSize, total, records);
    }

    public PageResult<Map<String, Object>> pageActivities(
            int pageNum,
            int pageSize,
            String productName,
            Integer status,
            LocalDateTime startFrom,
            LocalDateTime startTo,
            LocalDateTime endFrom,
            LocalDateTime endTo) {
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
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
        if (endFrom != null) {
            where.append(" and a.end_time >= ?");
            params.add(endFrom);
        }
        if (endTo != null) {
            where.append(" and a.end_time <= ?");
            params.add(endTo);
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

    public PageResult<Map<String, Object>> pageInventories(
            int pageNum,
            int pageSize,
            String productName,
            Integer availableGte,
            Integer availableLte) {
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (productName != null && !productName.isBlank()) {
            where.append(" and p.name like ?");
            params.add("%" + productName.trim() + "%");
        }
        if (availableGte != null) {
            where.append(" and i.available_stock >= ?");
            params.add(availableGte);
        }
        if (availableLte != null) {
            where.append(" and i.available_stock <= ?");
            params.add(availableLte);
        }

        String from = """
                from product_inventory i
                join product p on p.id = i.product_id
                """;
        long total = queryCount("select count(*) " + from + where, params);
        if (total == 0) {
            throw new NoSuchElementException("data not found");
        }

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(normalizedPageSize);
        dataParams.add(offset);
        List<Map<String, Object>> records = queryRows("""
                        select i.product_id, p.name as product_name,
                               i.available_stock, i.locked_stock, i.sold_stock,
                               i.created_at, i.updated_at
                        """ + from + where + " order by i.product_id asc limit ? offset ?",
                dataParams);
        return PageResult.of(normalizedPageNum, normalizedPageSize, total, records);
    }

    private Duration jitter(Duration base) {
        return base.plusSeconds(ThreadLocalRandom.current().nextLong(30, 120));
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

    private String productOrderBy(String sort, String name, List<Object> dataParams) {
        String normalizedSort = sort == null || sort.isBlank() ? "relevance" : sort.trim();
        return switch (normalizedSort) {
            case "priceAsc" -> " order by status desc, price_cent asc, id asc";
            case "priceDesc" -> " order by status desc, price_cent desc, id asc";
            case "latest" -> " order by status desc, created_at desc, id desc";
            case "idAsc" -> " order by id asc";
            case "relevance" -> {
                if (name != null && !name.isBlank()) {
                    String keyword = name.trim();
                    dataParams.add(keyword);
                    dataParams.add(keyword + "%");
                    yield """
                             order by
                               case
                                 when name = ? then 0
                                 when name like ? then 1
                                 else 2
                               end,
                               status desc,
                               id asc
                            """;
                }
                yield " order by status desc, created_at desc, id desc";
            }
            default -> throw new IllegalArgumentException("unsupported product sort: " + normalizedSort);
        };
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
}
