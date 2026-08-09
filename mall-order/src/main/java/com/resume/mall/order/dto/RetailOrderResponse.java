package com.resume.mall.order.dto;

import com.resume.mall.order.entity.RetailOrder;

import java.time.LocalDateTime;

public record RetailOrderResponse(
        String orderNo,
        Long userId,
        Long productId,
        Integer quantity,
        Long amountCent,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime completedAt
) {
    public static RetailOrderResponse from(RetailOrder order) {
        return new RetailOrderResponse(
                order.getOrderNo(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                order.getAmountCent(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getCompletedAt());
    }
}
