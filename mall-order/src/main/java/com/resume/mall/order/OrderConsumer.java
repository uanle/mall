package com.resume.mall.order;

import com.resume.mall.common.OrderCreateMessage;
import com.resume.mall.common.RabbitNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderConsumer {
    private final JdbcClient jdbcClient;

    public OrderConsumer(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    @RabbitListener(queues = RabbitNames.ORDER_CREATE_QUEUE)
    public void createOrder(OrderCreateMessage message) {
        try {
            jdbcClient.sql("""
                            insert into trade_order(order_no, user_id, activity_id, product_id, amount_cent, status, request_id)
                            values (?, ?, ?, ?, ?, 'NEW', ?)
                            """)
                    .param(orderNo(message))
                    .param(message.userId())
                    .param(message.activityId())
                    .param(message.productId())
                    .param(message.amountCent())
                    .param(message.requestId())
                    .update();

            jdbcClient.sql("""
                            insert into stock_deduct_log(request_id, user_id, activity_id, status, reason)
                            values (?, ?, ?, 'ORDER_CREATED', null)
                            """)
                    .param(message.requestId())
                    .param(message.userId())
                    .param(message.activityId())
                    .update();
        } catch (DuplicateKeyException ignored) {
            // Unique indexes make repeated MQ delivery idempotent.
        }
    }

    private String orderNo(OrderCreateMessage message) {
        return "SO" + message.activityId() + message.userId() + Math.abs(message.requestId().hashCode());
    }
}
