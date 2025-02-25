package com.traders.exchange.application;

import com.traders.common.model.InstrumentDTO;
import com.traders.common.model.InstrumentInfo;
import com.traders.common.model.MarketDetailsRequest;
import com.traders.common.model.MarketQuotes;
import com.traders.common.properties.ConfigProperties;
import com.traders.common.service.RedisService;
import com.traders.exchange.domain.ExchangePort;
import com.traders.exchange.domain.OrderMatchingPort;
import com.traders.exchange.domain.SubscriptionCommand;
import com.traders.exchange.domain.TransactionCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Corrected ExchangeFacade to preserve base subscriptions from getInstrumentsToSubScribe.
 */
@Service
public class ExchangeFacade {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeFacade.class);

    private final Map<String, ExchangePort> exchangeAdapters;
    private final CommandBus<SubscriptionCommand> subscriptionBus;
    private final OrderMatchingPort orderMatchingPort;
    private final ConfigProperties configProperties;
    private final RedisService redisService;

    public ExchangeFacade(List<ExchangePort> adapters, CommandBus<SubscriptionCommand> subscriptionBus,
                          OrderMatchingPort orderMatchingPort, ConfigProperties configProperties,
                          RedisService redisService) {
        this.exchangeAdapters = adapters.stream()
                .collect(Collectors.toMap(adapter -> adapter.getClass().getSimpleName().replace("Adapter", ""), adapter -> adapter));
        this.subscriptionBus = subscriptionBus;
        this.orderMatchingPort = orderMatchingPort;
        this.configProperties = configProperties;
        this.redisService = redisService;
        logger.info("ExchangeFacade initialized with vendor: {}", configProperties.getVendor());
    }

    /**
     * Initializes the exchange for the configured vendor.
     */
    public void initialize() {
        String vendor = configProperties.getVendor();
        validateVendor(vendor);
        exchangeAdapters.get(vendor).initialize();
        logger.info("Initialized exchange for vendor: {}", vendor);
    }

    /**
     * Subscribes to instruments via WebSocket for the configured vendor and marks them as base subscriptions.
     * These subscriptions persist and are not unsubscribed by getQuotes.
     * @param request The MarketDetailsRequest containing instruments to subscribe.
     */
    public void subscribe(MarketDetailsRequest request) {
        String vendor = configProperties.getVendor();
        validateVendor(vendor);
        List<InstrumentInfo> instrumentInfoList = new ArrayList<>();
        request.getSubscribeInstrumentDetailsList().forEach(d -> instrumentInfoList.add(new InstrumentInfo() {
            @Override public Long getInstrumentToken() { return d.getInstrumentId(); }
            @Override public String getExchangeSegment() { return d.getExchange(); }
            @Override public String getTradingSymbol() { return d.getInstrumentName(); }
        }));

        // Subscribe via WebSocket
        subscriptionBus.dispatch(vendor, new SubscriptionCommand.Subscribe(instrumentInfoList));
        logger.info("Subscribed to {} instruments (base) for vendor: {}", instrumentInfoList.size(), vendor);

        // Store in Redis under a persistent key (no TTL for base subscriptions)
        String redisKey = "base:subscribedInstruments"; // Global base key, not user-specific
        String subscriptions = instrumentInfoList.stream()
                .map(instrument -> instrument.getInstrumentToken().toString())
                .collect(Collectors.joining(","));
        redisService.saveToSessionCacheWithTTL(redisKey, subscriptions, 0, TimeUnit.HOURS); // 0 TTL means no expiration
    }

    /**
     * Fetches quotes via REST and manages WebSocket subscriptions for the configured vendor.
     * Unsubscribes only previous getQuotes subscriptions for the user, leaving base subscriptions intact.
     * @param instruments List of instruments to fetch quotes for.
     * @param userId Unique identifier for the user (e.g., from request header).
     * @return Map of instrument IDs to MarketQuotes.
     */
    public Map<String, MarketQuotes> getQuotes(List<InstrumentInfo> instruments, String userId) {
        String vendor = configProperties.getVendor();
        validateVendor(vendor);
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID must not be null or empty");
        }

        // Fetch quotes via REST
        Map<String, MarketQuotes> quotes = exchangeAdapters.get(vendor).fetchQuotes(instruments);
        logger.info("Fetched {} quotes for user {} from vendor: {}", quotes.size(), userId, vendor);

        // Manage WebSocket subscriptions (only for getQuotes, not base)
        String redisKey = "user:" + userId + ":quotesSubscribedInstruments"; // Distinct key for getQuotes

        // Get previous getQuotes subscriptions from Redis
        Object previousValue = redisService.getValue(redisKey);
        Set<String> previousSubscriptions = previousValue instanceof String
                ? Set.of(((String) previousValue).split(","))
                : Set.of();

        // Unsubscribe previous getQuotes instruments if they exist
        if (!previousSubscriptions.isEmpty()) {
            List<InstrumentInfo> toUnsubscribe = instruments.stream()
                    .filter(instrument -> previousSubscriptions.contains(instrument.getInstrumentToken().toString()))
                    .toList();
            if (!toUnsubscribe.isEmpty()) {
                //currently not unsubscribing
               // subscriptionBus.dispatch(vendor, new SubscriptionCommand.Unsubscribe(toUnsubscribe));
                logger.info("Unsubscribed {} getQuotes instruments for user {} from vendor: {}", toUnsubscribe.size(), userId, vendor);
            }
        }

        // Subscribe new instruments
        subscriptionBus.dispatch(vendor, new SubscriptionCommand.Subscribe(instruments));
        logger.info("Subscribed {} instruments for user {} from vendor: {}", instruments.size(), userId, vendor);

        // Update Redis with new getQuotes subscriptions
        String newSubscriptions = instruments.stream()
                .map(instrument -> instrument.getInstrumentToken().toString())
                .collect(Collectors.joining(","));
        redisService.saveToSessionCacheWithTTL(redisKey, newSubscriptions, 24, TimeUnit.HOURS);

        return quotes;
    }

    /**
     * Fetches instruments from the configured vendor.
     * @return List of InstrumentDTO objects.
     */
    public List<InstrumentDTO> getInstruments() {
        String vendor = configProperties.getVendor();
        validateVendor(vendor);
        List<InstrumentDTO> instruments = exchangeAdapters.get(vendor).fetchInstruments();
        logger.info("Fetched {} instruments from vendor: {}", instruments.size(), vendor);
        return instruments;
    }

    /**
     * Places an order (buy/sell) via the order matching service.
     * @param command The TransactionCommand (e.g., PlaceBuy, PlaceSell).
     */
    public void placeOrder(TransactionCommand command) {
        orderMatchingPort.executeTransaction(command);
        logger.info("Placed order: {}", command);
    }

    /**
     * Restarts the WebSocket session for the configured vendor.
     */
    public void restartSocket() {
        String vendor = configProperties.getVendor();
        validateVendor(vendor);
        exchangeAdapters.get(vendor).restartSession();
        logger.info("Restarted WebSocket session for vendor: {}", vendor);
    }

    private void validateVendor(String vendor) {
        if (vendor == null || !exchangeAdapters.containsKey(vendor)) {
            throw new IllegalArgumentException("Invalid or unsupported vendor: " + vendor);
        }
    }
}