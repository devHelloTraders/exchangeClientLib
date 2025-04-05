package com.traders.exchange.websocket;

import com.traders.common.model.PortfolioSubscriber;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebSocket subscriptions for users and portfolios.
 * Tracks user subscriptions (sessionId -> items) and portfolio subscriptions (sessionId -> PortfolioSubscriber).
 */
@Service
public class WebSocketSubscriptionService {
    private final Map<String, Set<String>> userSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, PortfolioSubscriber> portfolioSubscriptions = new ConcurrentHashMap<>();

    /**
     * Returns an unmodifiable view of all user subscriptions.
     * @return Map of session ID to subscribed items
     */
    public Map<String, Set<String>> getUserSubscriptions() {
        return Collections.unmodifiableMap(userSubscriptions);
    }

    /**
     * Returns an unmodifiable view of all portfolio subscriptions.
     * @return Map of session ID to PortfolioSubscriber
     */
    public Map<String, PortfolioSubscriber> getPortfolioSubscriptions() {
        return Collections.unmodifiableMap(portfolioSubscriptions);
    }

    /**
     * Subscribes a client to a list of items, replacing any existing subscriptions for the session.
     * @param sessionId WebSocket session ID
     * @param items List of items to subscribe to
     */
    public void subscribeFromClient(String sessionId, List<String> items) {
        Objects.requireNonNull(sessionId, "Session ID cannot be null");
        Objects.requireNonNull(items, "Items list cannot be null");

        unsubscribeAll(sessionId); // Clear existing subscriptions
        userSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).addAll(items);
    }

    /**
     * Subscribes a portfolio for a session.
     * @param sessionId WebSocket session ID
     * @param portfolioData Portfolio subscriber data
     * @param <T> Type extending PortfolioSubscriber
     */
    public <T extends PortfolioSubscriber> void subscribePortfolio(String sessionId, T portfolioData) {
        Objects.requireNonNull(sessionId, "Session ID cannot be null");
        Objects.requireNonNull(portfolioData, "Portfolio data cannot be null");

        portfolioSubscriptions.put(sessionId, portfolioData);
    }

    /**
     * Unsubscribes a session from specific items.
     * @param sessionId WebSocket session ID
     * @param items List of items to unsubscribe from
     */
    public void unsubscribe(String sessionId, List<String> items) {
        Objects.requireNonNull(sessionId, "Session ID cannot be null");
        Objects.requireNonNull(items, "Items list cannot be null");

        Set<String> subscriptions = userSubscriptions.get(sessionId);
        if (subscriptions != null) {
            items.forEach(subscriptions::remove);
            if (subscriptions.isEmpty()) {
                userSubscriptions.remove(sessionId);
            }
        }
    }

    /**
     * Unsubscribes a session from all items.
     * @param sessionId WebSocket session ID
     */
    public void unsubscribeAll(String sessionId) {
        Objects.requireNonNull(sessionId, "Session ID cannot be null");
        userSubscriptions.remove(sessionId);
    }

    /**
     * Removes all subscriptions (user and portfolio) for a session.
     * @param sessionId WebSocket session ID
     */
    public void removeSession(String sessionId) {
        Objects.requireNonNull(sessionId, "Session ID cannot be null");
        userSubscriptions.remove(sessionId);
        portfolioSubscriptions.remove(sessionId);
    }

    /**
     * Gets the set of subscribed items for a session.
     * @param sessionId WebSocket session ID
     * @return Set of subscribed items, or empty set if none
     */
    public Set<String> getSubscriptions(String sessionId) {
        Objects.requireNonNull(sessionId, "Session ID cannot be null");
        return Collections.unmodifiableSet(userSubscriptions.getOrDefault(sessionId, Collections.emptySet()));
    }
}