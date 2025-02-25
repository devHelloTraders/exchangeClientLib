// com.traders.exchange.infrastructure.dhan.DhanExchangeAdapter
package com.traders.exchange.infrastructure.dhan;

import com.traders.common.model.InstrumentDTO;
import com.traders.common.model.InstrumentInfo;
import com.traders.common.model.MarketQuotes;
import com.traders.exchange.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DhanExchangeAdapter implements ExchangePort {
    private final DhanCredentialFactory credentialFactory;
    private final DhanConnectionPool connectionPool;
    private final DhanQuoteProvider quoteProvider;
    private final DhanInstrumentFetcher instrumentFetcher;
    private final DhanConfig config;

    public DhanExchangeAdapter(DhanCredentialFactory credentialFactory, DhanConnectionPool connectionPool,
                               DhanQuoteProvider quoteProvider, DhanInstrumentFetcher instrumentFetcher,
                               DhanConfig config) {
        this.credentialFactory = credentialFactory;
        this.connectionPool = connectionPool;
        this.quoteProvider = quoteProvider;
        this.instrumentFetcher = instrumentFetcher;
        this.config = config;
    }

    @Override
    public void initialize() {
        if (!config.active()) return;
        connectionPool.initialize(); // Uses credential via DhanWebSocketFactory
    }

    @Override
    public List<InstrumentDTO> fetchInstruments() {
        DhanCredentialFactory.Credential credential = credentialFactory.getRandomCredential();
        return instrumentFetcher.fetchInstruments(credential);
    }

    @Override
    public void executeSubscription(SubscriptionCommand command) {
        connectionPool.execute(command);
    }

    @Override
    public Map<String, MarketQuotes> fetchQuotes(List<InstrumentInfo> instruments) {
        DhanCredentialFactory.Credential credential = credentialFactory.getRandomCredential();
        return quoteProvider.fetchQuotes(instruments, credential);
    }

    @Override
    public void restartSession() {
        connectionPool.restart();
    }
}