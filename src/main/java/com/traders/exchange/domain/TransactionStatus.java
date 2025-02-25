package com.traders.exchange.domain;

import java.time.LocalDateTime;

public enum TransactionStatus {
    PENDING, COMPLETED, CANCELLED;

    public LocalDateTime completedTime() {
        return this == COMPLETED ? LocalDateTime.now() : null;
    }
}