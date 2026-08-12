package com.resume.mall.seckill.config;

import com.resume.mall.common.RabbitNames;
import com.resume.mall.observability.LogValues;
import com.resume.mall.seckill.service.SeckillPublishCompensationService;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class RabbitConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitConfig.class);

    private final SeckillPublishCompensationService compensationService;

    public RabbitConfig(SeckillPublishCompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @Bean
    DirectExchange orderExchange() {
        return new DirectExchange(RabbitNames.ORDER_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(RabbitNames.ORDER_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue orderCreateQueue() {
        return QueueBuilder.durable(RabbitNames.ORDER_CREATE_QUEUE)
                .deadLetterExchange(RabbitNames.ORDER_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.ORDER_CREATE_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue orderCreateDeadLetterQueue() {
        return QueueBuilder.durable(RabbitNames.ORDER_CREATE_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding orderCreateBinding(DirectExchange orderExchange, Queue orderCreateQueue) {
        return BindingBuilder.bind(orderCreateQueue)
                .to(orderExchange)
                .with(RabbitNames.ORDER_CREATE_ROUTING_KEY);
    }

    @Bean
    Binding orderCreateDeadLetterBinding(DirectExchange orderDeadLetterExchange, Queue orderCreateDeadLetterQueue) {
        return BindingBuilder.bind(orderCreateDeadLetterQueue)
                .to(orderDeadLetterExchange)
                .with(RabbitNames.ORDER_CREATE_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return rabbitTemplate -> {
            rabbitTemplate.setConfirmCallback((CorrelationData correlation, boolean ack, String cause) -> {
                String requestId = correlation == null ? null : correlation.getId();
                if (requestId == null || requestId.isBlank()) {
                    return;
                }
                if (ack) {
                    compensationService.markPublished(requestId);
                    return;
                }
                LOGGER.atError()
                        .addKeyValue("event", "order_message_publish_nacked")
                        .addKeyValue("requestId", LogValues.safe(requestId))
                        .addKeyValue("reason", cause)
                        .log("Broker rejected order creation message");
                compensationService.compensatePublishFailure(requestId, "broker nack: " + cause);
            });
            rabbitTemplate.setReturnsCallback(returned -> {
                Object requestId = returned.getMessage().getMessageProperties().getHeaders().get("requestId");
                if (!(requestId instanceof String value) || value.isBlank()) {
                    return;
                }
                LOGGER.atError()
                        .addKeyValue("event", "order_message_returned")
                        .addKeyValue("requestId", LogValues.safe(value))
                        .addKeyValue("replyCode", returned.getReplyCode())
                        .addKeyValue("replyText", returned.getReplyText())
                        .addKeyValue("exchange", returned.getExchange())
                        .addKeyValue("routingKey", returned.getRoutingKey())
                        .log("Order creation message was returned by broker");
                compensationService.compensatePublishFailure(value, "message returned: " + returned.getReplyText());
            });
        };
    }

    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
