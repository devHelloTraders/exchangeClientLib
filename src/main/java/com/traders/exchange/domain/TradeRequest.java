// com.traders.exchange.domain.TradeRequest
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
    OrderValidity orderValidity
) {

}