package com.resume.mall.order.config;

import com.resume.mall.common.RabbitNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
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
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
