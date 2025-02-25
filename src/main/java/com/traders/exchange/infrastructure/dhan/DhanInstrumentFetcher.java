// com.traders.exchange.infrastructure.dhan.DhanInstrumentFetcher
package com.traders.exchange.infrastructure.dhan;

import com.traders.common.model.InstrumentDTO;
import com.traders.common.model.InstrumentInfo;
import com.traders.exchange.domain.CategorizedInstrumentInfo;
import com.traders.exchange.util.CsvParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DhanInstrumentFetcher {
    private final OkHttpClient client;
    private final DhanConfig config;
    private final CsvParser csvParser;
    private final DhanExchangeResolver exchangeResolver;

    public DhanInstrumentFetcher(OkHttpClient client, DhanConfig config, CsvParser csvParser, DhanExchangeResolver exchangeResolver) {
        this.client = client;
        this.config = config;
        this.csvParser = csvParser;
        this.exchangeResolver = exchangeResolver;
    }

    public List<InstrumentDTO> fetchInstruments() {
        return fetchInstruments(null); // Default to no credentials if not needed
    }

    public List<InstrumentDTO> fetchInstruments(DhanCredentialFactory.Credential credential) {
        Request.Builder requestBuilder = new Request.Builder().url(config.instrumentUrl());
        if (credential != null) {
            requestBuilder.header("access-token", credential.apiKey())
                    .header("client-id", credential.clientId());
        }
        Request request = requestBuilder.build();
        try (Response response = client.newCall(request).execute()) {
            String csv = response.body().string();
            return csvParser.parse(csv).stream().map(instrument->{
                instrument.setExchangeSegment(exchangeResolver.resolveCategory(instrument));
                return instrument;
            }).toList();
        } catch (IOException e) {
            throw new RuntimeException("Instrument fetch failed: " + e.getMessage(), e);
        }
    }
}