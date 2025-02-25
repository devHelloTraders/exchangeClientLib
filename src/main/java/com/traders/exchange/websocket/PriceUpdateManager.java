package com.traders.exchange.websocket;

import com.traders.common.model.MarketQuotes;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PriceUpdateManager {
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSubscriptionService subscriptionService;

    public PriceUpdateManager(SimpMessagingTemplate messagingTemplate, WebSocketSubscriptionService subscriptionService) {
        this.messagingTemplate = messagingTemplate;
        this.subscriptionService = subscriptionService;
    }

    public void sendPriceUpdate(String instrumentId, MarketQuotes priceUpdate) {
        subscriptionService.getUserSubscriptions().forEach((sessionId, subscribedInstruments) -> {
            if (subscribedInstruments.contains(instrumentId)) {
                messagingTemplate.convertAndSendToUser(sessionId, "/topic/update", priceUpdate);
            }
        });
    }
}