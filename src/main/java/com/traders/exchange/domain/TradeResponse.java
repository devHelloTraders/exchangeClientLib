// com.traders.exchange.orders.TradeResponse
package com.traders.exchange.domain;

import lombok.Builder;

@Builder
public record TradeResponse(
    TradeRequest request,
    Long transactionId,
    String instrumentId
) {
    public Double getAskedPrice() {
        return request.askedPrice();
    }

    public Double getStopLossPrice() {
        return request.stopLossPrice();
    }
}