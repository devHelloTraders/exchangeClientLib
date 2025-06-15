package com.traders.exchange.infrastructure.twelvedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traders.common.model.InstrumentInfo;
import com.traders.common.model.MarketQuotes;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TwelveDataQuoteProvider {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private final OkHttpClient client;
    private final TwelveDataResponseHandler responseHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TwelveDataQuoteProvider(OkHttpClient client, TwelveDataResponseHandler responseHandler) {
        this.client = client;
        this.responseHandler = responseHandler;
    }

    public Map<String, MarketQuotes> fetchQuotes(List<InstrumentInfo> instruments, TwelveDataCredentialFactory.Credential credential) {
        Map<String, List<Long>> groupedByExchange = instruments.stream()
                .collect(Collectors.groupingBy(
                        InstrumentInfo::getExchangeSegment,
                        Collectors.mapping(InstrumentInfo::getInstrumentToken, Collectors.toList())
                ));
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(groupedByExchange);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create quote request body: " + e.getMessage(), e);
        }

        Request request = new Request.Builder()
                .url("https://api.dhan.co/v2/marketfeed/quote")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("access-token", credential.apiKey())
                .header("client-id", credential.clientId())
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String json = response.body().string();
            return responseHandler.parseRestResponse(json);
        } catch (IOException e) {
            throw new RuntimeException("Quote fetch failed: " + e.getMessage(), e);
        }
    }
}