package com.traders.exchange.infrastructure.dhan;

import com.traders.exchange.orders.service.OrderMatchingService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.concurrent.Executors;

@Component
public class DhanWebSocketFactory {
    private final DhanResponseHandler responseHandler;
    private final OrderMatchingService orderMatchingService;

    public DhanWebSocketFactory(DhanResponseHandler responseHandler, OrderMatchingService orderMatchingService) {
        this.responseHandler = responseHandler;
        this.orderMatchingService = orderMatchingService;
    }

    public DhanConnectionPool.DhanConnection createConnection(DhanCredentialFactory.Credential credential) {
        String url = "wss://api-feed.dhan.co?version=2&token=%s&clientId=%s&authType=2"
                .formatted(credential.apiKey(), credential.clientId());
        DhanWebSocketHandler handler = new DhanWebSocketHandler(responseHandler, orderMatchingService);
        WebSocketConnectionManager manager = WebSocketConnectionManagerBuilder.builder()
                .withClient(new StandardWebSocketClient())
                .withHandler(handler)
                .withUrl(url)
                .build();
        return new DhanConnectionPool.DhanConnection(manager, Executors.newVirtualThreadPerTaskExecutor());
    }
}