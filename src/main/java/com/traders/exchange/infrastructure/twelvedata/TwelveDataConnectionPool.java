package com.traders.exchange.infrastructure.twelvedata;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.traders.common.model.InstrumentInfo;
import com.traders.exchange.domain.SubscriptionCommand;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TwelveDataConnectionPool {
    private static final int SUBSCRIBE_REQUEST_CODE = 21;
    private static final int UNSUBSCRIBE_REQUEST_CODE = 22;
    private static final long HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MS = 500; // 1 second

    private final TwelveDataCredentialFactory credentialFactory;
    private final TwelveDataWebSocketFactory webSocketFactory;
    @Getter private final List<TwelveDataConnection> connections = new CopyOnWriteArrayList<>();
    private final CircuitBreaker circuitBreaker;

    public TwelveDataConnectionPool(TwelveDataCredentialFactory credentialFactory, TwelveDataWebSocketFactory webSocketFactory) {
        this.credentialFactory = credentialFactory;
        this.webSocketFactory = webSocketFactory;
        this.circuitBreaker = CircuitBreaker.ofDefaults("TwelveDataWebSocket");
    }

    public void initialize() {
        connections.clear();
        credentialFactory.getCredentials().forEach(credential -> {
            connections.add(webSocketFactory.createConnection(credential));
        });
        log.info("Initialized TwelveDataConnectionPool with {} connections", connections.size());
    }

    public void execute(SubscriptionCommand command) {
        TwelveDataConnection conn = getLeastLoadedConnection();
        Runnable subscriptionTask = () -> {
            switch (command) {
                case SubscriptionCommand.Subscribe(var instruments) -> conn.subscribe(instruments);
                case SubscriptionCommand.Unsubscribe(var instruments) -> conn.unsubscribe(instruments);
            }
        };
        circuitBreaker.executeRunnable(subscriptionTask);
    }

    private TwelveDataConnection getLeastLoadedConnection() {
        return connections.stream()
                .min(Comparator.comparingInt(TwelveDataConnection::getLoad))
                .orElseGet(() -> createNewConnection(credentialFactory.getRandomCredential()));
    }

    private TwelveDataConnection createNewConnection(TwelveDataCredentialFactory.Credential credential) {
        TwelveDataConnection conn = webSocketFactory.createConnection(credential);
        connections.add(conn);
        return conn;
    }

    public void restart() {
        connections.forEach(TwelveDataConnection::restart);
    }

    public static class TwelveDataConnection {
        private final WebSocketConnectionManager manager;
        private final Executor executor;
        private final TwelveDataWebSocketHandler handler;
        private final ScheduledExecutorService heartbeatExecutor;
        @Getter private volatile boolean isConnected;
        private int reconnectAttempts;
        @Getter private LocalDateTime startTime;
        @Getter private LocalDateTime lastReceivedTime;
        @Getter private int subscriptionCount;
        @Getter private LocalDateTime lastPingSent;
        @Getter private LocalDateTime lastPongReceived;
        public TwelveDataConnection(WebSocketConnectionManager manager, Executor executor, TwelveDataWebSocketHandler handler) {
            this.manager = manager;
            this.executor = executor;
            this.handler = handler;
            this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
            this.isConnected = false;
            this.reconnectAttempts = 0;
            startConnection();
           // startHeartbeat();
        }

        private void startConnection() {
            executor.execute(() -> {
                try {
                    manager.start();
                    isConnected = true;
                    reconnectAttempts = 0;
                    startTime = LocalDateTime.now();
                    log.info("TwelveData Connection started");
                } catch (Exception e) {
                    log.error("Failed to start TwelveData Connection: {}", e.getMessage(), e);
                    reconnect();
                }
            });
        }
        public void updateLastPongReceived() {
            this.lastPongReceived = LocalDateTime.now();
        }
        public void updateLastReceivedTime() {
            this.lastReceivedTime = LocalDateTime.now();
        }
        public void subscribe(List<InstrumentInfo> instruments) {
            if(instruments.isEmpty())
                return;
            executor.execute(() -> {
                try {
                    WebSocketSession session = handler.getSession();
                    if (session != null && session.isOpen()) {
                        String subscriptionMessage = createSubscriptionMessage(instruments, SUBSCRIBE_REQUEST_CODE);
                        log.info("Subscribed to {}", subscriptionMessage);
                        session.sendMessage(new TextMessage(subscriptionMessage));
                        log.info("Subscribed to {} instruments", instruments.size());
                    } else {
                        log.warn("WebSocket session not open for subscription");
                        reconnect();
                    }
                } catch (IOException e) {
                    log.error("Failed to subscribe: {}", e.getMessage(), e);
                    reconnect();
                }
            });
        }

        public void unsubscribe(List<InstrumentInfo> instruments) {
            if(instruments.isEmpty())
                return;
            executor.execute(() -> {
                try {
                    WebSocketSession session = handler.getSession();
                    if (session != null && session.isOpen()) {
                        String unsubscribeMessage = createSubscriptionMessage(instruments, UNSUBSCRIBE_REQUEST_CODE);
                        session.sendMessage(new TextMessage(unsubscribeMessage));
                        log.info("Unsubscribed from {} instruments", instruments.size());
                    } else {
                        log.warn("WebSocket session not open for unsubscription");
                        reconnect();
                    }
                } catch (IOException e) {
                    log.error("Failed to unsubscribe: {}", e.getMessage(), e);
                    reconnect();
                }
            });
        }

        private String createSubscriptionMessage(List<InstrumentInfo> instruments, int requestCode) {
            String instrumentListJson = instruments.stream()
                    .map(instrument -> String.format(
                            "{\"ExchangeSegment\": \"%s\", \"SecurityId\": \"%s\"}",
                            instrument.getExchangeSegment(),
                            instrument.getInstrumentToken()
                    ))
                    .collect(Collectors.joining(","));
            return "{\"RequestCode\": %d, \"InstrumentCount\": %d, \"InstrumentList\": [%s]}"
                    .formatted(requestCode, instruments.size(), instrumentListJson);
        }

        public int getLoad() {
            return 1; // Could track active subscriptions if needed
        }

        public void restart() {
            manager.stop();
            executor.execute(manager::start);
        }

        private JsonObject createPingPayload(WebSocketSession session) {
            JsonObject payload = new JsonObject();
            payload.add("sessionId", new JsonPrimitive(session.getId()));
            payload.add("pingedAt", new JsonPrimitive(LocalDateTime.now().toString()));
            return payload;
        }
        private void startHeartbeat() {
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                WebSocketSession session = handler.getSession();
                if (isConnected && session != null && session.isOpen()) {
                    try {
                        JsonObject payloadJson = createPingPayload(session); //  Maximum allowed payload of 125 bytes only
                        ByteBuffer payload = ByteBuffer.wrap(payloadJson.toString().getBytes());
                        session.sendMessage(new PingMessage(payload));
                        lastPingSent = LocalDateTime.now(); // Update last ping sent
                        log.debug("Sent heartbeat ping at {}", lastPingSent);
                        log.debug("Sent heartbeat ping");
                    } catch (Exception e) {
                        log.warn("Heartbeat failed: {}", e.getMessage());
                        reconnect();
                    }
                }
            }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        void reconnect() {
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                log.error("Max reconnect attempts ({}) reached. Giving up.", MAX_RECONNECT_ATTEMPTS);
                return;
            }
            isConnected =false;
            long backoff = INITIAL_BACKOFF_MS * (1L << reconnectAttempts);
            reconnectAttempts++;
            log.info("Attempting reconnect #{} after {}ms backoff", reconnectAttempts, backoff);

            executor.execute(() -> {
                try {
                    Thread.sleep(backoff);
                    if (!isConnected) {
                        manager.stop();
                        manager.start();
                        isConnected = true;
                        log.info("Reconnection successful");
                    }
                    this.reconnectAttempts=0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Reconnect attempt interrupted", e);
                } catch (Exception e) {
                    log.error("Reconnection failed: {}", e.getMessage(), e);
                }
            });
        }
    }
}