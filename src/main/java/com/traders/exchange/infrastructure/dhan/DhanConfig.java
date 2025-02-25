package com.traders.exchange.infrastructure.dhan;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
@ConfigurationProperties(prefix = "exchange.dhan")
public record DhanConfig(
    String apiKey,
    String apiSecret,
    String instrumentUrl,
    List<String> apiCredentials,
    int allowedConnection,
    boolean active
) {
    public DhanConfig {
        if (apiCredentials == null) apiCredentials = new ArrayList<>();
    }

}