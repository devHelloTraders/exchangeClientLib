package com.traders.exchange.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class Subject<T> {
    private final List<Consumer<T>> observers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<T> observer) {
        observers.add(observer);
    }

    public void notifyObservers(T event) {
        observers.forEach(observer -> observer.accept(event));
    }
}