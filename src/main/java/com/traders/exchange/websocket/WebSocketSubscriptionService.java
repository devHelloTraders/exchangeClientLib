package com.traders.exchange.websocket;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketSubscriptionService {
    private final Map<String, Set<String>> userSubscriptions = new ConcurrentHashMap<>();

    public Map<String, Set<String>> getUserSubscriptions() {
        return Collections.unmodifiableMap(userSubscriptions);
    }

    public void subscribeFromClient(String sessionId, List<String> items) {
        userSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).addAll(items);
    }
}