package com.traders.exchange.infrastructure.twelvedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traders.common.model.MarketQuotes;
import com.traders.common.service.RedisService;
import com.traders.exchange.websocket.PriceUpdateManager;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class TwelveDataResponseHandler {
    private final PriceUpdateManager priceUpdateManager;
    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TwelveDataResponseHandler(PriceUpdateManager priceUpdateManager, RedisService redisService) {
        this.priceUpdateManager = priceUpdateManager;
        this.redisService = redisService;
    }

    public void handlePriceUpdate(MarketQuotes quote) {
        priceUpdateManager.sendPriceUpdate(quote.getInstrumentName(), quote);
        redisService.addStockCache(quote.getInstrumentName(), quote);
    }

    public Map<String, MarketQuotes> parseRestResponse(String json) {
        try {
            MarketQuotesResponse response = objectMapper.readValue(json, MarketQuotesResponse.class);
            Map<String, MarketQuotes> quotesMap = new HashMap<>();
            response.data().values().forEach(quotesMap::putAll);
            return quotesMap;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse REST response: " + e.getMessage(), e);
        }
    }

    // Inner record for deserialization
    record MarketQuotesResponse(String status, Map<String, Map<String, MarketQuotes>> data) {}
}