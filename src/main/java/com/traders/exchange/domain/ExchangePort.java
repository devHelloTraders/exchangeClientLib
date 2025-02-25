package com.traders.exchange.domain;

import com.traders.common.model.InstrumentDTO;
import com.traders.common.model.InstrumentInfo;
import com.traders.common.model.MarketQuotes;

import java.util.List;
import java.util.Map;

public interface ExchangePort {
    void initialize();
    List<InstrumentDTO> fetchInstruments();
    void executeSubscription(SubscriptionCommand command);
    Map<String, MarketQuotes> fetchQuotes(List<InstrumentInfo> instruments);
    void restartSession();
}