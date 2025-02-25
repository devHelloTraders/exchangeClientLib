package com.traders.exchange.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.support.AbstractMessageChannel;

@Configuration
@EnableWebSocketMessageBroker
public class MessagingConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic"); // In-memory broker for /topic destinations
        config.setApplicationDestinationPrefixes("/app"); // For application messages, if needed
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS(); // WebSocket endpoint for clients
    }

    @Bean
    @Primary
    public SimpMessagingTemplate simpMessagingTemplate(AbstractMessageChannel brokerChannel) {
        return new SimpMessagingTemplate(brokerChannel); // No setDestinationPrefix needed
    }
}