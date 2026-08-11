package com.resume.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resume.mall.common.PageResult;
import com.resume.mall.common.RedisKeys;
import com.resume.mall.product.dto.CreateInventoryRequest;
import com.resume.mall.product.dto.CreateProductRequest;
import com.resume.mall.product.dto.UpdateInventoryRequest;
import com.resume.mall.product.dto.UpdateProductRequest;
import com.resume.mall.product.entity.Product;
import com.resume.mall.product.mapper.ProductMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public Map<String, Object> createProduct(CreateProductRequest request) {
        int status = normalizeStatus(request.status(), 1);
        long productId = request.id() == null ? generateProductId() : request.id();
        Product product = new Product();
        product.setId(productId);
        product.setName(requireName(request.name()));
        product.setPriceCent(requirePositivePrice(request.priceCent()));
        product.setStatus(status);
        try {
            productMapper.insert(product);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("product id already exists");
        }
        redisTemplate.delete(RedisKeys.productCache(productId));
        return getProductAnyStatus(productId);
    }

    @Transactional
    public Map<String, Object> updateProduct(long productId, UpdateProductRequest request) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new NoSuchElementException("product not found");
        }
        if (request.name() != null) {
            product.setName(requireName(request.name()));
        }
        if (request.priceCent() != null) {
            product.setPriceCent(requirePositivePrice(request.priceCent()));
        }
        if (request.status() != null) {
            product.setStatus(normalizeStatus(request.status(), product.getStatus()));
        }
        productMapper.updateById(product);
        redisTemplate.delete(RedisKeys.productCache(productId));
        return getProductAnyStatus(productId);
    }

    @Transactional
    public void deleteProduct(long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new NoSuchElementException("product not found");
        }
        product.setStatus(0);
        productMapper.updateById(product);
        redisTemplate.delete(RedisKeys.productCache(productId));
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

    @Transactional
    public Map<String, Object> createInventory(CreateInventoryRequest request) {
        ensureProductExists(request.productId());
        try {
            jdbcClient.sql("""
                            insert into product_inventory(product_id, available_stock, locked_stock, sold_stock)
                            values (?, ?, ?, ?)
                            """)
                    .param(request.productId())
                    .param(request.availableStock())
                    .param(request.lockedStock() == null ? 0 : request.lockedStock())
                    .param(request.soldStock() == null ? 0 : request.soldStock())
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("inventory already exists");
        }
        return getInventory(request.productId());
    }

    @Transactional
    public Map<String, Object> updateInventory(long productId, UpdateInventoryRequest request) {
        getInventory(productId);
        StringBuilder set = new StringBuilder();
        List<Object> params = new ArrayList<>();
        appendInventorySet(set, params, "available_stock", request.availableStock());
        appendInventorySet(set, params, "locked_stock", request.lockedStock());
        appendInventorySet(set, params, "sold_stock", request.soldStock());
        if (params.isEmpty()) {
            return getInventory(productId);
        }
        params.add(productId);
        JdbcClient.StatementSpec spec = jdbcClient.sql("update product_inventory set " + set + " where product_id = ?");
        for (Object param : params) {
            spec = spec.param(param);
        }
        spec.update();
        return getInventory(productId);
    }

    @Transactional
    public void deleteInventory(long productId) {
        int updated = jdbcClient.sql("delete from product_inventory where product_id = ?")
                .param(productId)
                .update();
        if (updated == 0) {
            throw new NoSuchElementException("inventory not found");
        }
    }

    public Map<String, Object> getInventory(long productId) {
        List<Map<String, Object>> rows = queryRows("""
                        select i.product_id, p.name as product_name,
                               i.available_stock, i.locked_stock, i.sold_stock,
                               i.created_at, i.updated_at
                        from product_inventory i
                        join product p on p.id = i.product_id
                        where i.product_id = ?
                        """,
                List.of(productId));
        if (rows.isEmpty()) {
            throw new NoSuchElementException("inventory not found");
        }
        return rows.get(0);
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

    private Map<String, Object> getProductAnyStatus(long productId) {
        List<Map<String, Object>> rows = queryRows("""
                        select id, name, price_cent, status, created_at, updated_at
                        from product
                        where id = ?
                        """,
                List.of(productId));
        if (rows.isEmpty()) {
            throw new NoSuchElementException("product not found");
        }
        return rows.get(0);
    }

    private void ensureProductExists(long productId) {
        long count = jdbcClient.sql("select count(*) from product where id = ?")
                .param(productId)
                .query(Long.class)
                .single();
        if (count == 0) {
            throw new NoSuchElementException("product not found");
        }
    }

    private long generateProductId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.trim();
    }

    private long requirePositivePrice(Long priceCent) {
        if (priceCent == null || priceCent < 1) {
            throw new IllegalArgumentException("priceCent must be greater than 0");
        }
        return priceCent;
    }

    private int normalizeStatus(Integer status, int defaultValue) {
        int value = status == null ? defaultValue : status;
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
        return value;
    }

    private void appendInventorySet(StringBuilder set, List<Object> params, String column, Integer value) {
        if (value == null) {
            return;
        }
        if (value < 0) {
            throw new IllegalArgumentException(column + " must be non-negative");
        }
        if (!set.isEmpty()) {
            set.append(", ");
        }
        set.append(column).append(" = ?");
        params.add(value);
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
