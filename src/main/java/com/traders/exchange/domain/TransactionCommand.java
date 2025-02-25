package com.traders.exchange.domain;

public sealed interface TransactionCommand {
    record PlaceBuy(TradeResponse tradeResponse) implements TransactionCommand {}
    record PlaceSell(TradeResponse tradeResponse) implements TransactionCommand {}
    record UpdateStatus(long transactionId, TransactionStatus status, double price) implements TransactionCommand {}
}