package com.traders.exchange.infrastructure.twelvedata;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "exchange.twelvedata")
public record TwelveDataConfig(
    String apiKey,
    String apiSecret,
    String instrumentUrl,
    List<String> apiCredentials,
    int allowedConnection,
    boolean active
) {
    public TwelveDataConfig {
        if (apiCredentials == null) apiCredentials = new ArrayList<>();
    }

}