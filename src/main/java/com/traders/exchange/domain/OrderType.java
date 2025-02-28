// com.traders.exchange.domain.OrderType
package com.traders.exchange.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.traders.exchange.orders.service.OrderMatchingService;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum OrderType {
    BUY {
        @Override
        public void placeOrder(TradeResponse tradeResponse, OrderMatchingService service) {
            service.placeBuyOrder(tradeResponse);
        }

        @Override
        public Integer getQuantity(Integer quantity) {
            return quantity;
        }
    },
    SELL {
        @Override
        public void placeOrder(TradeResponse tradeResponse, OrderMatchingService service) {
            service.placeSellOrder(tradeResponse);
        }

        @Override
        public Integer getQuantity(Integer quantity) {
            return -quantity;
        }
    };

    public abstract void placeOrder(TradeResponse tradeResponse, OrderMatchingService service);
    public abstract Integer getQuantity(Integer quantity);
}