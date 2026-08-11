package com.resume.mall.order.dto;

import java.util.List;

public record CheckoutCartRequest(
        List<Long> itemIds
) {
}
