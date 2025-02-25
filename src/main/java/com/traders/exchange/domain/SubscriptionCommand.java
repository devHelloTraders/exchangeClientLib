package com.traders.exchange.domain;

import com.traders.common.model.InstrumentInfo;

import java.util.List;

public sealed interface SubscriptionCommand {
    record Subscribe(List<InstrumentInfo> instruments) implements SubscriptionCommand {}
    record Unsubscribe(List<InstrumentInfo> instruments) implements SubscriptionCommand {}
}