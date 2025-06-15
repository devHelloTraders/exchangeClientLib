package com.traders.exchange.infrastructure.twelvedata;

import com.traders.common.model.InstrumentDTO;
import com.traders.common.model.InstrumentInfo;
import com.traders.common.model.MarketQuotes;
import com.traders.exchange.domain.ExchangePort;
import com.traders.exchange.domain.SubscriptionCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TwelveDataExchangeAdapter implements ExchangePort {
    private static final Logger logger = LoggerFactory.getLogger(TwelveDataExchangeAdapter.class);

    private final TwelveDataCredentialFactory credentialFactory;
    private final TwelveDataConnectionPool connectionPool;
    private final TwelveDataQuoteProvider quoteProvider;
    private final TwelveDataInstrumentFetcher instrumentFetcher;
    private final TwelveDataConfig config;

    public TwelveDataExchangeAdapter(TwelveDataCredentialFactory credentialFactory, TwelveDataConnectionPool connectionPool,
                                     TwelveDataQuoteProvider quoteProvider, TwelveDataInstrumentFetcher instrumentFetcher,
                                     TwelveDataConfig config) {
        this.credentialFactory = credentialFactory;
        this.connectionPool = connectionPool;
        this.quoteProvider = quoteProvider;
        this.instrumentFetcher = instrumentFetcher;
        this.config = config;
    }

    @Override
    public void initialize() {
        if (!config.active()) return;
        connectionPool.initialize();
        logger.info("TwelveData Adapter initialized");
    }

    @Override
    public List<InstrumentDTO> fetchInstruments() {
        TwelveDataCredentialFactory.Credential credential = credentialFactory.getRandomCredential();
        return instrumentFetcher.fetchInstruments(credential);
    }

    @Override
    public void executeSubscription(SubscriptionCommand command) {
        connectionPool.execute(command);
    }

    @Override
    public Map<String, MarketQuotes> fetchQuotes(List<InstrumentInfo> instruments) {
        TwelveDataCredentialFactory.Credential credential = credentialFactory.getRandomCredential();
        return quoteProvider.fetchQuotes(instruments, credential);
    }

    @Override
    public void restartSession() {
        connectionPool.restart();
    }
}