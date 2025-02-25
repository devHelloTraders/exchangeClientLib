// com.traders.exchange.infrastructure.dhan.DhanWebSocketHandler
package com.traders.exchange.infrastructure.dhan;

import com.traders.common.model.MarketQuotes;
import com.traders.exchange.orders.service.OrderMatchingService;
import com.traders.exchange.util.Subject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executors;

@Slf4j
public class DhanWebSocketHandler extends AbstractWebSocketHandler {
    private final DhanResponseHandler responseHandler;
    private final OrderMatchingService orderMatchingService;
    private final Subject<MarketQuotes> priceUpdates;
    @Getter
    private volatile WebSocketSession session;
    @Setter
    private DhanConnectionPool.DhanConnection ownerConnection; // Reference for reconnection

    public DhanWebSocketHandler(DhanResponseHandler responseHandler, OrderMatchingService orderMatchingService) {
        this.responseHandler = responseHandler;
        this.orderMatchingService = orderMatchingService;
        this.priceUpdates = new Subject<>();
        priceUpdates.subscribe(responseHandler::handlePriceUpdate);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        log.info("WebSocket connection established for session: {}", session.getId());
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer buffer = message.getPayload();
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] responseHeader = new byte[8];
        buffer.get(responseHeader);
        byte feedResponseCode = responseHeader[0];

        if (feedResponseCode == 50) {
            short disconnectCode = buffer.getShort();
            log.info("Disconnection Code: {}", disconnectCode);
        } else if (feedResponseCode == 8) {
            String instrumentId = extractInstrumentId(responseHeader);
            MarketQuotes quote = MarketQuotes.parseFromByteBuffer(buffer, instrumentId);
            if (quote.getLatestTradedPrice() == 0) return;
            Executors.newVirtualThreadPerTaskExecutor().execute(()->{
                priceUpdates.notifyObservers(quote);
                orderMatchingService.onPriceUpdate(instrumentId, quote);
            });

        } else {
            log.warn("Unhandled feed response code: {}", feedResponseCode);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket error: {}", exception.getMessage(), exception);
        attemptReconnect();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: {}", status);
        this.session = null;
        attemptReconnect();
    }

    private void attemptReconnect() {
        if (ownerConnection != null) {
            ownerConnection.reconnect();
        } else {
            log.warn("No owner connection set for reconnection");
        }
    }

    private String extractInstrumentId(byte[] responseHeader) {
        return String.valueOf(ByteBuffer.wrap(responseHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }
}