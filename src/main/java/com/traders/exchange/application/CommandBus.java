package com.traders.exchange.application;

import com.traders.exchange.domain.ExchangePort;
import com.traders.exchange.domain.SubscriptionCommand;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class CommandBus<T> {
    private final Map<String, Consumer<T>> handlers;

    public CommandBus(List<ExchangePort> adapters) {
        this.handlers = adapters.stream()
            .collect(Collectors.toMap(
                adapter -> adapter.getClass().getSimpleName().replace("Adapter", ""),
                adapter -> cmd -> ((ExchangePort) adapter).executeSubscription((SubscriptionCommand) cmd)
            ));
    }

    public void dispatch(String target, T command) {
        handlers.get(target).accept(command);
    }
}