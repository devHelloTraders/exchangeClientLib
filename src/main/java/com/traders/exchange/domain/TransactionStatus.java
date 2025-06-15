package com.traders.exchange.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum TransactionStatus {
    PENDING, COMPLETED, CANCELLED;
}