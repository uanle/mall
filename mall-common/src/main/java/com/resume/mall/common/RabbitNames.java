package com.resume.mall.common;

public final class RabbitNames {
    public static final String ORDER_EXCHANGE = "mall.order.exchange";
    public static final String ORDER_CREATE_QUEUE = "mall.order.create.queue";
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create";

    private RabbitNames() {
    }
}
