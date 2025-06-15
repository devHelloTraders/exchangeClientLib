package com.traders.exchange.domain;

public record TransactionUpdateRecord(
    Long id,
    Double price,
    TransactionStatus transactionStatus
) {}