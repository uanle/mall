package com.resume.mall.seckill.config;

import com.resume.mall.common.RabbitNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    DirectExchange orderExchange() {
        return new DirectExchange(RabbitNames.ORDER_EXCHANGE, true, false);
    }

    @Bean
    Queue orderCreateQueue() {
        return new Queue(RabbitNames.ORDER_CREATE_QUEUE, true);
    }

    @Bean
    Binding orderCreateBinding(DirectExchange orderExchange, Queue orderCreateQueue) {
        return BindingBuilder.bind(orderCreateQueue)
                .to(orderExchange)
                .with(RabbitNames.ORDER_CREATE_ROUTING_KEY);
    }

    @Bean
    RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return rabbitTemplate -> rabbitTemplate.setConfirmCallback((CorrelationData correlation, boolean ack, String cause) -> {
            if (!ack) {
                throw new IllegalStateException("message publish not confirmed: " + cause);
            }
        });
    }

    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
