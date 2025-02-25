// com.traders.exchange.infrastructure.dhan.DhanConnectionPool
package com.traders.exchange.infrastructure.dhan;

import com.traders.common.model.InstrumentInfo;
import com.traders.exchange.domain.SubscriptionCommand;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DhanConnectionPool {
    private static final int SUBSCRIBE_REQUEST_CODE = 21;
    private static final int UNSUBSCRIBE_REQUEST_CODE = 22;

    private final DhanCredentialFactory credentialFactory;
    private final DhanWebSocketFactory webSocketFactory;
    private final List<DhanConnection> connections = new CopyOnWriteArrayList<>();
    private final CircuitBreaker circuitBreaker;

    public DhanConnectionPool(DhanCredentialFactory credentialFactory, DhanWebSocketFactory webSocketFactory) {
        this.credentialFactory = credentialFactory;
        this.webSocketFactory = webSocketFactory;
        this.circuitBreaker = CircuitBreaker.ofDefaults("dhanWebSocket");
    }

    public void initialize() {
        connections.clear();
        credentialFactory.getCredentials().forEach(credential ->  {
            connections.add(webSocketFactory.createConnection(credential));
           // this.createNewConnection(credential);
        });
    }

    public void execute(SubscriptionCommand command) {
        DhanConnection conn = getLeastLoadedConnection();
        Runnable subscriptionTask = () -> {
            switch (command) {
                case SubscriptionCommand.Subscribe(var instruments) -> conn.subscribe(instruments);
                case SubscriptionCommand.Unsubscribe(var instruments) -> conn.unsubscribe(instruments);
            }
        };
        circuitBreaker.executeRunnable(subscriptionTask);
    }

    private DhanConnection getLeastLoadedConnection() {
        return connections.stream()
                .min(Comparator.comparingInt(DhanConnection::getLoad))
                .orElseGet(()->createNewConnection(credentialFactory.getRandomCredential()));
    }

    private DhanConnection createNewConnection(DhanCredentialFactory.Credential credential) {
        DhanConnection conn = webSocketFactory.createConnection(credential);
        connections.add(conn);
        return conn;
    }

    public void restart() {
        connections.forEach(DhanConnection::restart);
    }

    record DhanConnection(WebSocketConnectionManager manager, Executor executor) {
        public void subscribe(List<InstrumentInfo> instruments) {
            executor.execute(() -> {
                try {
                   // manager.start();
                    WebSocketSession session = ((WebSocketConnectionManagerBuilder.CustomWebSocketConnectionManager) manager).getSession();
                    if (session != null && session.isOpen()) {
                        String subscriptionMessage = createSubscriptionMessage(instruments, SUBSCRIBE_REQUEST_CODE);
                        session.sendMessage(new TextMessage(subscriptionMessage));
                        log.info("Subscribed to {} instruments", instruments.size());
                    } else {
                        log.warn("WebSocket session not open for subscription");
                    }
                } catch (IOException e) {
                    log.error("Failed to subscribe: {}", e.getMessage(), e);
                }
            });
        }

        public void unsubscribe(List<InstrumentInfo> instruments) {
            executor.execute(() -> {
                try {
                    WebSocketSession session = ((WebSocketConnectionManagerBuilder.CustomWebSocketConnectionManager) manager).getSession();
                    if (session != null && session.isOpen()) {
                        String unsubscribeMessage = createSubscriptionMessage(instruments, UNSUBSCRIBE_REQUEST_CODE);
                        session.sendMessage(new TextMessage(unsubscribeMessage));
                        log.info("Unsubscribed from {} instruments", instruments.size());
                    } else {
                        log.warn("WebSocket session not open for unsubscription");
                    }
                } catch (IOException e) {
                    log.error("Failed to unsubscribe: {}", e.getMessage(), e);
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
    }
}