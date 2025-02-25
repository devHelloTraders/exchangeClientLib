// com.traders.exchange.domain.CategorizedInstrumentInfo
package com.traders.exchange.domain;

import com.traders.common.model.InstrumentInfo;

public class CategorizedInstrumentInfo implements InstrumentInfo {
    private final Long instrumentToken;
    private final String exchangeSegment;
    private final String tradingSymbol;
    private final String category;

    public CategorizedInstrumentInfo(Long instrumentToken, String exchangeSegment, String tradingSymbol, String category) {
        this.instrumentToken = instrumentToken;
        this.exchangeSegment = exchangeSegment;
        this.tradingSymbol = tradingSymbol;
        this.category = category;
    }

    @Override
    public Long getInstrumentToken() {
        return instrumentToken;
    }

    @Override
    public String getExchangeSegment() {
        return exchangeSegment;
    }

    @Override
    public String getTradingSymbol() {
        return tradingSymbol;
    }

    public String getCategory() {
        return category;
    }
}