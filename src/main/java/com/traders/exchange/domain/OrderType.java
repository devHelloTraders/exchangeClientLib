// com.traders.exchange.domain.OrderType
package com.traders.exchange.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum OrderType {
    BUY {
        @Override
        public Integer getQuantity(Integer quantity) {
            return quantity;
        }
    },
    SELL {
        @Override
        public Integer getQuantity(Integer quantity) {
            return -quantity;
        }
    };

    public abstract Integer getQuantity(Integer quantity);
}