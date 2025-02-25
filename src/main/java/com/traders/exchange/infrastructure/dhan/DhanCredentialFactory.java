package com.traders.exchange.infrastructure.dhan;

import com.traders.common.utils.EncryptionUtil;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@Component
public class DhanCredentialFactory {
    private final DhanConfig config;

    @Getter
    private final List<Credential> credentials = new CopyOnWriteArrayList<>();

    public record Credential(String clientId, String apiKey) {}

    public DhanCredentialFactory(DhanConfig config) {
        this.config = config;
        initializeCredentials();
    }

    private void initializeCredentials() {
        credentials.addAll(config.apiCredentials().stream()
                        .flatMap (x -> IntStream.range(0,config.allowedConnection()).mapToObj(i ->x))
            .map(this::createCredential)
            .toList());
    }

    private Credential createCredential(String raw) {
        raw = EncryptionUtil.decrypt(raw);

        String[] parts = raw.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid credential format");
        return new Credential(parts[0].trim(), parts[1].trim());
    }

    public Credential getRandomCredential() {
        return credentials.get(ThreadLocalRandom.current().nextInt(credentials.size()));
    }


}