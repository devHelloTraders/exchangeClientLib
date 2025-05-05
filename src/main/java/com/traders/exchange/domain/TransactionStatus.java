package com.traders.exchange.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum TransactionStatus {
    PENDING, COMPLETED, CANCELLED;

    public LocalDateTime completedTime() {
        return this == COMPLETED ? LocalDateTime.now() : null;
    }
}