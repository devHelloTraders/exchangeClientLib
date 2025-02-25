// com.traders.exchange.orders.service.OrderMatchingService
package com.traders.exchange.orders.service;

import com.traders.common.model.MarketQuotes;
import com.traders.exchange.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Advanced OrderMatchingService with optimized performance, incorporating postProcessOrder from OrderCategory.
 */
@Service
public class OrderMatchingService implements OrderMatchingPort {
    private static final Logger logger = LoggerFactory.getLogger(OrderMatchingService.class);

    private final Map<String, ConcurrentSkipListSet<TradeResponse>> buyOrderQueues = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentSkipListSet<TradeResponse>> sellOrderQueues = new ConcurrentHashMap<>();
    private final Set<Long> loadedTransactionIds = ConcurrentHashMap.newKeySet();
    private final Map<String, ReadWriteLock> stockLocks = new ConcurrentHashMap<>();

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private final TradeFeignService tradeFeign;
    private final BlockingQueue<OrderTask> orderTaskQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isProcessingOrders = new AtomicBoolean(false);

    public OrderMatchingService(TradeFeignService tradeFeign) {
        this.tradeFeign = tradeFeign;
        startOrderProcessor();
    }

    @Override
    public void executeTransaction(TransactionCommand command) {
        orderTaskQueue.offer(new OrderTask(command));
    }

    @Override
    public void onPriceUpdate(String instrumentId, MarketQuotes quote) {
        if (quote.getLatestTradedPrice() == 0) return;
        executor.execute(() -> processPriceUpdate(instrumentId, quote.getLatestTradedPrice()));
    }

    private void startOrderProcessor() {
        executor.execute(() -> {
            while (true) {
                try {
                    OrderTask task = orderTaskQueue.take();
                    if (isProcessingOrders.compareAndSet(false, true)) {
                        try {
                            processOrderTask(task);
                        } finally {
                            isProcessingOrders.set(false);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Order processor interrupted", e);
                    break;
                }
            }
        });
    }

    private void processOrderTask(OrderTask task) {
        TransactionCommand command = task.command;
        switch (command) {
            case TransactionCommand.PlaceBuy(var req) -> {
                try {
                    req.request().orderCategory().validateTradeRequest(req.request());
                    placeOrder(req, true);
                    req.request().orderCategory().postProcessOrder(this, req); // Pass this instance
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid buy order request: {}", e.getMessage());
                }
            }
            case TransactionCommand.PlaceSell(var req) -> {
                try {
                    req.request().orderCategory().validateTradeRequest(req.request());
                    placeOrder(req, false);
                    req.request().orderCategory().postProcessOrder(this, req); // Pass this instance
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid sell order request: {}", e.getMessage());
                }
            }
            case TransactionCommand.UpdateStatus(var id, var status, var price) ->
                tradeFeign.updateTradeTransaction(new TransactionUpdateRecord(id, price, status));
        }
    }

    private void processPriceUpdate(String stockSymbol, Double price) {
        ReadWriteLock lock = stockLocks.computeIfAbsent(stockSymbol, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            processOrdersForPrice(stockSymbol, price);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void processOrdersForPrice(String stockSymbol, Double price) {
        processOrders(stockSymbol, price, buyOrderQueues, true);
        processOrders(stockSymbol, price, sellOrderQueues, false);
    }

    private void processOrders(String stockSymbol, Double price, Map<String, ConcurrentSkipListSet<TradeResponse>> orderQueues, boolean isBuy) {
        ConcurrentSkipListSet<TradeResponse> orders = orderQueues.get(stockSymbol);
        if (orders == null || orders.isEmpty()) return;

        Iterator<TradeResponse> iterator = orders.iterator();
        while (iterator.hasNext()) {
            TradeResponse order = iterator.next();
            TradeRequest request = order.request();
            boolean shouldMatch = shouldMatchOrder(request.orderCategory(), request.askedPrice(), request.stopLossPrice(), request.targetPrice(), price, isBuy);

            if (shouldMatch) {
                iterator.remove();
                TransactionUpdateRecord updateRecord = new TransactionUpdateRecord(
                    order.transactionId(), price, TransactionStatus.COMPLETED
                );
                completeTransaction(updateRecord);
            } else {
                break;
            }
        }
    }

    private boolean shouldMatchOrder(OrderCategory category, Double askedPrice, Double stopLossPrice, Double targetPrice, Double price, boolean isBuy) {
        return switch (category) {
            case MARKET -> true;
            case LIMIT -> isBuy ? askedPrice <= price : askedPrice >= price;
            case BRACKET_AT_MARKET -> isBuy
                ? askedPrice <= price && (targetPrice == 0 || price <= targetPrice)
                : stopLossPrice >= price && (targetPrice == 0 || price >= targetPrice);
            case BRACKET_AT_LIMIT -> isBuy ? askedPrice <= price : askedPrice >= price;
            case STOP_LOSS -> stopLossPrice >= price;
        };
    }

    private void completeTransaction(TransactionUpdateRecord order) {
        tradeFeign.updateTradeTransaction(order);
        loadedTransactionIds.remove(order.id());
        logger.debug("Completed transaction: {}", order);
    }

    private void placeOrder(TradeResponse order, boolean isBuy) {
        if (loadedTransactionIds.contains(order.transactionId())) return;
        String stockSymbol = order.instrumentId();
        Map<String, ConcurrentSkipListSet<TradeResponse>> queues = isBuy ? buyOrderQueues : sellOrderQueues;
        Comparator<TradeResponse> comparator = isBuy
            ? Comparator.comparingDouble(TradeResponse::getAskedPrice)
            : Comparator.comparingDouble(TradeResponse::getStopLossPrice).reversed();

        queues.computeIfAbsent(stockSymbol, k -> new ConcurrentSkipListSet<>(comparator))
            .add(order);
        loadedTransactionIds.add(order.transactionId());
        logger.debug("Placed {} order for stock {}: {}", isBuy ? "buy" : "sell", stockSymbol, order);
    }

    // Public methods for OrderCategory.postProcessOrder
    public void placeBuyOrder(TradeResponse order) {
        placeOrder(order, true);
    }

    public void placeSellOrder(TradeResponse order) {
        placeOrder(order, false);
    }

    private record OrderTask(TransactionCommand command) {}
}