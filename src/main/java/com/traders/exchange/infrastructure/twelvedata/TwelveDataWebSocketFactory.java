package com.traders.exchange.infrastructure.twelvedata;

import com.traders.exchange.orders.service.OrderMatchingService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.concurrent.Executors;

@Component
public class TwelveDataWebSocketFactory {
    private final TwelveDataResponseHandler responseHandler;
    private final OrderMatchingService orderMatchingService;

    public TwelveDataWebSocketFactory(TwelveDataResponseHandler responseHandler, OrderMatchingService orderMatchingService) {
        this.responseHandler = responseHandler;
        this.orderMatchingService = orderMatchingService;
    }

    public TwelveDataConnectionPool.TwelveDataConnection createConnection(TwelveDataCredentialFactory.Credential credential) {
        String url = "wss://ws.twelvedata.com/v1/quotes/price?apikey=%s"
                .formatted(credential.apiKey());
        TwelveDataWebSocketHandler handler = new TwelveDataWebSocketHandler(responseHandler, orderMatchingService);
        WebSocketConnectionManager manager = WebSocketConnectionManagerBuilder.builder()
                .withClient(new StandardWebSocketClient())
                .withHandler(handler)
                .withUrl(url)
                .build();
        TwelveDataConnectionPool.TwelveDataConnection connection = new TwelveDataConnectionPool.TwelveDataConnection(manager, Executors.newVirtualThreadPerTaskExecutor(), handler);
        handler.setOwnerConnection(connection); // Set after creation
        return connection;
    }
}