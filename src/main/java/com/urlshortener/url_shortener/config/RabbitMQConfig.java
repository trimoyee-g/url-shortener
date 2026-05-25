package com.urlshortener.url_shortener.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String CLICK_QUEUE      = "url.clicks";
    public static final String CLICK_EXCHANGE   = "url.clicks.exchange";
    public static final String CLICK_ROUTING_KEY = "url.clicks";

    @Bean
    public Queue clickQueue() {
        return QueueBuilder.durable(CLICK_QUEUE).build();
    }

    @Bean
    public DirectExchange clickExchange() {
        return new DirectExchange(CLICK_EXCHANGE);
    }

    @Bean
    public Binding clickBinding(Queue clickQueue, DirectExchange clickExchange) {
        return BindingBuilder.bind(clickQueue).to(clickExchange).with(CLICK_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        return factory;
    }
}
