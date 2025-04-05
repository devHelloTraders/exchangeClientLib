package com.traders.exchange.domain;

import lombok.Builder;


@Builder
public record TradeRequest(
        Double lotSize,
        OrderType orderType,
        OrderCategory orderCategory,
        Long stockId,
        Double askedPrice,
        Double stopLossPrice,
        Double targetPrice,
        OrderValidity orderValidity,
        Long transactionId
) {

    public TradeRequest withAskedPrice(Double newPrice) {
        return new TradeRequest(
                this.lotSize,
                this.orderType,
                this.orderCategory,
                this.stockId,
                newPrice,
                this.stopLossPrice,
                this.targetPrice,
                this.orderValidity,
                this.transactionId
        );
    }
}