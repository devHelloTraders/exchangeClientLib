package com.traders.exchange.infrastructure.dhan.management;

import com.traders.exchange.infrastructure.dhan.DhanConnectionPool;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Endpoint(id = "dhanwebsockets")
public class DhanWebSocketEndpoint implements VendorWebsocket{

    private final DhanConnectionPool connectionPool;
    private final MeterRegistry meterRegistry;

    // Gauges for metrics
    private final AtomicInteger totalConnectionsGauge = new AtomicInteger(0);
    private final AtomicInteger connectedCountGauge = new AtomicInteger(0);
    private final AtomicInteger subscriptionCountGauge = new AtomicInteger(0);
    private  LocalDateTime lastPongRecived;

    public DhanWebSocketEndpoint(@Lazy DhanConnectionPool connectionPool, MeterRegistry meterRegistry) {
        this.connectionPool = connectionPool;
        this.meterRegistry = meterRegistry;
    }


    @PostConstruct
    public void initMetrics() {
        meterRegistry.gauge("dhan.websockets.totalConnections", totalConnectionsGauge);
        meterRegistry.gauge("dhan.websockets.connectedCount", connectedCountGauge);
        meterRegistry.gauge("dhan.websockets.subscriptionCount", subscriptionCountGauge);
    }

    @ReadOperation
    public WebSocketStatus getWebSocketStatus() {
        List<ConnectionInfo> connections = connectionPool.getConnections().stream()
                .map(conn -> new ConnectionInfo(
                        conn.isConnected(),
                        conn.getStartTime(),
                        conn.getLastReceivedTime(),
                        conn.getSubscriptionCount(),
                        conn.getLastPingSent(),
                        conn.getLastPongReceived()
                ))
                .collect(Collectors.toList());

        // Update gauge values
        totalConnectionsGauge.set(connections.size());
        connectedCountGauge.set((int) connections.stream().filter(ConnectionInfo::isConnected).count());
        subscriptionCountGauge.set(connections.stream().mapToInt(ConnectionInfo::getSubscriptionCount).sum());

        return new WebSocketStatus(connections.size(), connections);
    }



    @WriteOperation
    public void restartConnections() {
        connectionPool.restart();
        getWebSocketStatus(); // Refresh metrics after action
    }

    @WriteOperation
    public void performOperation(@Selector String operation) {
        switch (operation) {

            case "restartConnections":
                connectionPool.restart();
                break;
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    @Data
    public static class WebSocketStatus {
        private final int totalConnections;
        private final List<ConnectionInfo> connections;
    }


    @Data
    public static class ConnectionInfo {
        private final boolean connected;
        private final LocalDateTime startTime;
        private final LocalDateTime lastReceivedTime;
        private final int subscriptionCount;
        private final LocalDateTime lastPingSent;
        private final LocalDateTime lastPongReceived;
    }
}