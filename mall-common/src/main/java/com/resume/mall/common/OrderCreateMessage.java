package com.resume.mall.common;

import java.io.Serializable;
import java.time.Instant;

public record OrderCreateMessage(
        String requestId,
        long userId,
        long activityId,
        long productId,
        long amountCent,
        Instant reservedAt
) implements Serializable {
}
