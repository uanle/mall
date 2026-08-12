package com.resume.mall.common;

public final class RabbitNames {
    public static final String ORDER_EXCHANGE = "mall.order.exchange";
    public static final String ORDER_CREATE_QUEUE = "mall.order.create.queue";
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create";
    public static final String ORDER_DEAD_LETTER_EXCHANGE = "mall.order.dlx";
    public static final String ORDER_CREATE_DEAD_LETTER_QUEUE = "mall.order.create.dlq";
    public static final String ORDER_CREATE_DEAD_LETTER_ROUTING_KEY = "order.create.dlq";

    private RabbitNames() {
    }
}
