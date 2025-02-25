package com.traders.exchange.domain;

public record TransactionUpdateRecord(
    long id,
    double price,
    TransactionStatus transactionStatus
) {}