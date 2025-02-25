// com.traders.exchange.infrastructure.dhan.DhanWebSocketHandler
package com.traders.exchange.infrastructure.dhan;

import com.traders.common.model.MarketQuotes;
import com.traders.exchange.orders.service.OrderMatchingService;
import com.traders.exchange.util.Subject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Slf4j
public class DhanWebSocketHandler extends AbstractWebSocketHandler {
    private final DhanResponseHandler responseHandler;
    private final OrderMatchingService orderMatchingService; // Added dependency
    private final Subject<MarketQuotes> priceUpdates;
    private volatile WebSocketSession session;

    public DhanWebSocketHandler(DhanResponseHandler responseHandler, OrderMatchingService orderMatchingService) {
        this.responseHandler = responseHandler;
        this.orderMatchingService = orderMatchingService;
        this.priceUpdates = new Subject<>();
        priceUpdates.subscribe(responseHandler::handlePriceUpdate);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        log.info("WebSocket connection established");
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
        } else if (feedResponseCode == 8) { // Quote response code
            String instrumentId = extractInstrumentId(responseHeader);
            MarketQuotes quote = MarketQuotes.parseFromByteBuffer(buffer, instrumentId);
            if(quote.getLatestTradedPrice() ==0)
                return;
            priceUpdates.notifyObservers(quote); // Notify PriceUpdateManager
            orderMatchingService.onPriceUpdate(instrumentId, quote); // Notify OrderMatchingService
        } else {
            log.warn("Unhandled feed response code: {}", feedResponseCode);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket error: {}", exception.getMessage(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        log.info("WebSocket connection closed: {}", status);
        this.session = null;
    }

    public WebSocketSession getSession() {
        return session;
    }

    private String extractInstrumentId(byte[] responseHeader) {
        return String.valueOf(ByteBuffer.wrap(responseHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }
}