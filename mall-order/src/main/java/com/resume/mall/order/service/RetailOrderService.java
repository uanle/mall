package com.resume.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.resume.mall.order.dto.CreateOrderRequest;
import com.resume.mall.order.dto.RetailOrderResponse;
import com.resume.mall.order.entity.ProductSnapshot;
import com.resume.mall.order.entity.RetailOrder;
import com.resume.mall.order.mapper.ProductInventoryMapper;
import com.resume.mall.order.mapper.ProductSnapshotMapper;
import com.resume.mall.order.mapper.RetailOrderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RetailOrderService {
    private static final String CREATED = "CREATED";
    private static final String PAID = "PAID";
    private static final String COMPLETED = "COMPLETED";

    private final ProductSnapshotMapper productMapper;
    private final ProductInventoryMapper inventoryMapper;
    private final RetailOrderMapper orderMapper;

    public RetailOrderService(
            ProductSnapshotMapper productMapper,
            ProductInventoryMapper inventoryMapper,
            RetailOrderMapper orderMapper) {
        this.productMapper = productMapper;
        this.inventoryMapper = inventoryMapper;
        this.orderMapper = orderMapper;
    }

    @Transactional
    public RetailOrderResponse create(long userId, CreateOrderRequest request, String idempotencyKey) {
        RetailOrder existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return RetailOrderResponse.from(existing);
        }

        ProductSnapshot product = productMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getId, request.productId())
                .eq(ProductSnapshot::getStatus, 1));
        if (product == null) {
            throw new IllegalArgumentException("product not found or inactive");
        }

        int reserved = inventoryMapper.reserveStock(request.productId(), request.quantity());
        if (reserved != 1) {
            throw new IllegalStateException("insufficient stock");
        }

        RetailOrder order = new RetailOrder();
        order.setOrderNo("RO" + System.currentTimeMillis() + Math.abs(UUID.randomUUID().hashCode()));
        order.setUserId(userId);
        order.setProductId(request.productId());
        order.setQuantity(request.quantity());
        order.setAmountCent(product.getPriceCent() * request.quantity());
        order.setStatus(CREATED);
        order.setIdempotencyKey(idempotencyKey);

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException ignored) {
            return RetailOrderResponse.from(findByIdempotencyKey(idempotencyKey));
        }
        return RetailOrderResponse.from(orderMapper.selectById(order.getId()));
    }

    @Transactional
    public RetailOrderResponse pay(String orderNo) {
        RetailOrder order = findByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (PAID.equals(order.getStatus()) || COMPLETED.equals(order.getStatus())) {
            return RetailOrderResponse.from(order);
        }
        if (!CREATED.equals(order.getStatus())) {
            throw new IllegalStateException("order cannot be paid in status " + order.getStatus());
        }

        orderMapper.update(null, new LambdaUpdateWrapper<RetailOrder>()
                .eq(RetailOrder::getOrderNo, orderNo)
                .eq(RetailOrder::getStatus, CREATED)
                .set(RetailOrder::getStatus, PAID)
                .set(RetailOrder::getPaidAt, LocalDateTime.now()));
        return RetailOrderResponse.from(findByOrderNo(orderNo));
    }

    @Transactional
    public RetailOrderResponse complete(String orderNo) {
        RetailOrder order = findByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (COMPLETED.equals(order.getStatus())) {
            return RetailOrderResponse.from(order);
        }
        if (!PAID.equals(order.getStatus())) {
            throw new IllegalStateException("order cannot be completed in status " + order.getStatus());
        }

        int confirmed = inventoryMapper.confirmSale(order.getProductId(), order.getQuantity());
        if (confirmed != 1) {
            throw new IllegalStateException("locked stock is inconsistent");
        }
        orderMapper.update(null, new LambdaUpdateWrapper<RetailOrder>()
                .eq(RetailOrder::getOrderNo, orderNo)
                .eq(RetailOrder::getStatus, PAID)
                .set(RetailOrder::getStatus, COMPLETED)
                .set(RetailOrder::getCompletedAt, LocalDateTime.now()));
        return RetailOrderResponse.from(findByOrderNo(orderNo));
    }

    public RetailOrder findByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<RetailOrder>()
                .eq(RetailOrder::getOrderNo, orderNo));
    }

    private RetailOrder findByIdempotencyKey(String idempotencyKey) {
        return orderMapper.selectOne(new LambdaQueryWrapper<RetailOrder>()
                .eq(RetailOrder::getIdempotencyKey, idempotencyKey));
    }
}
