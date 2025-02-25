// com.traders.exchange.infrastructure.dhan.WebSocketConnectionManagerBuilder
package com.traders.exchange.infrastructure.dhan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
public class WebSocketConnectionManagerBuilder {
    private StandardWebSocketClient client;
    private DhanWebSocketHandler handler;
    private String url;

    private WebSocketConnectionManagerBuilder() {}

    public static WebSocketConnectionManagerBuilder builder() {
        return new WebSocketConnectionManagerBuilder();
    }

    public WebSocketConnectionManagerBuilder withClient(StandardWebSocketClient client) {
        this.client = client;
        return this;
    }

    public WebSocketConnectionManagerBuilder withHandler(DhanWebSocketHandler handler) {
        this.handler = handler;
        return this;
    }

    public WebSocketConnectionManagerBuilder withUrl(String url) {
        this.url = url;
        return this;
    }

    public WebSocketConnectionManager build() {
        return new CustomWebSocketConnectionManager(client, handler, url);
    }

    public static class CustomWebSocketConnectionManager extends WebSocketConnectionManager {
        private final DhanWebSocketHandler handler;

        public CustomWebSocketConnectionManager(StandardWebSocketClient client, DhanWebSocketHandler handler, String url) {
            super(client, handler, url);
            this.handler = handler;
        }

        public WebSocketSession getSession() {
            return handler.getSession(); // Delegate to handler
        }

        public DhanWebSocketHandler getHandler() {
            return handler;
        }
    }
}