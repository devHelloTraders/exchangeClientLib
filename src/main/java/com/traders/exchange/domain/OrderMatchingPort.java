// com.traders.exchange.domain.OrderMatchingPort
package com.traders.exchange.domain;

import com.traders.common.model.MarketQuotes;

public interface OrderMatchingPort {
    void executeTransaction(TransactionCommand command);
    void onPriceUpdate(String instrumentId, MarketQuotes quote);
}