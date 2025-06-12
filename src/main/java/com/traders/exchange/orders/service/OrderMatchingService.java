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
 * Enhanced OrderMatchingService with order price update capability.
 * Optimized with Java 21 virtual threads and thread-safe collections.
 */
@Service
public class OrderMatchingService implements OrderMatchingPort {
    private static final Logger logger = LoggerFactory.getLogger(OrderMatchingService.class);

    private final Map<String, ConcurrentSkipListSet<TradeResponse>> buyOrderQueues = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentSkipListSet<TradeResponse>> sellOrderQueues = new ConcurrentHashMap<>();
    private final Set<Long> loadedTransactionIds = ConcurrentHashMap.newKeySet();
    private final Map<String, ReadWriteLock> stockLocks = new ConcurrentHashMap<>();
    private final Map<Long, TradeResponse> orderLookup = new ConcurrentHashMap<>(); // For fast order updates

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
        executor.execute(() -> processPriceUpdate(instrumentId, quote));
    }

    /**
     * Updates the price of an existing order in the queue.
     *
     * @param transactionId The ID of the order to update
     * @param newPrice      The new asked price
     * @return true if updated, false if order not found
     */
    public boolean updateOrderPrice(long transactionId, double newPrice, double priceWhenUpdated) {
        TradeResponse existing = orderLookup.get(transactionId);
        if (existing == null) {
            logger.warn("Order not found for update: {}", transactionId);
            return false;
        }

        String stockSymbol = existing.instrumentId();
        boolean isBuy = existing.request().orderType() == OrderType.BUY;
        Map<String, ConcurrentSkipListSet<TradeResponse>> queues = isBuy ? buyOrderQueues : sellOrderQueues;

        ConcurrentSkipListSet<TradeResponse> queue = queues.get(stockSymbol);
        if (queue == null || !queue.contains(existing)) {
            logger.warn("Order {} not in queue for stock {}", transactionId, stockSymbol);
            return false;
        }

        TradeRequest updatedRequest = existing.request().withAskedPrice(newPrice);
        TradeResponse updated = new TradeResponse(updatedRequest,
                existing.transactionId(),
                existing.instrumentId(),
                existing.isShortSell(),
                priceWhenUpdated
        );

        ReadWriteLock lock = stockLocks.computeIfAbsent(stockSymbol, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            queue.remove(existing);
            queue.add(updated);
            orderLookup.put(transactionId, updated);
            logger.info("Updated order {} price from {} to {} in {} queue for stock {}",
                    transactionId, existing.request().askedPrice(), newPrice, isBuy ? "buy" : "sell", stockSymbol);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
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
        switch (task.command) {
            case TransactionCommand.PlaceBuy(var req) -> {
                try {
                    req.request().orderCategory().validateTradeRequest(req.request());
                    placeOrder(req, true);
                    req.request().orderCategory().postProcessOrder(this, req);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid buy order request: {}", e.getMessage());
                }
            }
            case TransactionCommand.PlaceSell(var req) -> {
                try {
                    req.request().orderCategory().validateTradeRequest(req.request());
                    placeOrder(req, false);
                    req.request().orderCategory().postProcessOrder(this, req);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid sell order request: {}", e.getMessage());
                }
            }
            case TransactionCommand.UpdateStatus(var id, var status, var price) ->
                    tradeFeign.updateTradeTransaction(new TransactionUpdateRecord(id, price, status));
        }
    }

    private void processPriceUpdate(String stockSymbol, MarketQuotes quotes) {
        ReadWriteLock lock = stockLocks.computeIfAbsent(stockSymbol, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            processOrdersForPrice(stockSymbol, quotes);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void processOrdersForPrice(String stockSymbol, MarketQuotes quotes) {
        processOrders(stockSymbol, quotes, buyOrderQueues, true);
        processOrders(stockSymbol, quotes, sellOrderQueues, false);
    }

    private void processOrders(String stockSymbol, MarketQuotes quotes,
                               Map<String, ConcurrentSkipListSet<TradeResponse>> orderQueues, boolean isBuy) {
        ConcurrentSkipListSet<TradeResponse> orders = orderQueues.get(stockSymbol);
        if (orders == null || orders.isEmpty()) return;

        Iterator<TradeResponse> iterator = orders.iterator();
        while (iterator.hasNext()) {
            TradeResponse order = iterator.next();
            TradeRequest request = order.request();

            double price = isBuy
                    ? quotes.getDepthDetails().getSell().getFirst().getPrice()
                    : quotes.getDepthDetails().getBuy().getFirst().getPrice();

            boolean shouldMatch = shouldMatchOrder(
                    request.orderCategory(), request.askedPrice(), request.stopLossPrice(),
                    request.targetPrice(), price, order.priceWhenOrderPlaced(), isBuy, order.isShortSell()
            );

            if (shouldMatch) {
                logger.info("Price matched for transaction ID {}, Price When Order Placed : {} | Asked Price : {} | Executed Price : {}"
                        , request.transactionId(), order.priceWhenOrderPlaced(),order.getAskedPrice(),price);
                iterator.remove();
                TransactionUpdateRecord updateRecord = new TransactionUpdateRecord(
                        order.transactionId(), price, TransactionStatus.COMPLETED
                );
                completeTransaction(updateRecord);
                orderLookup.remove(order.transactionId());
            } else {
                break;
            }
        }
    }

    private boolean shouldMatchOrder(OrderCategory category, Double askedPrice, Double stopLossPrice,
                                     Double targetPrice, Double price, Double priceWhenOrderPlaced, boolean isBuy, boolean shortSell) {
        if(price == 0.0)
            return false;

        return switch (category) {
            case MARKET -> true;
            case LIMIT -> {
                boolean isPriceImproved = askedPrice < priceWhenOrderPlaced;
                if (isPriceImproved) {
                    yield price <= askedPrice;
                } else {
                    yield price >= askedPrice;
                }
            }
            case BRACKET_AT_MARKET -> isBuy
                    ? askedPrice >= price && (targetPrice == 0 || price >= targetPrice)
                    : stopLossPrice <= price && (targetPrice == 0 || price >= targetPrice);
            case BRACKET_AT_LIMIT -> isBuy ? askedPrice >= price : askedPrice <= price;
            case STOP_LOSS -> stopLossPrice <= price;
        };
    }

    private void completeTransaction(TransactionUpdateRecord order) {
        executor.execute(()->{
            tradeFeign.updateTradeTransaction(order);
            loadedTransactionIds.remove(order.id());
            logger.debug("Completed transaction: {}", order);
        });

    }

    private void placeOrder(TradeResponse order, boolean isBuy) {
        if (loadedTransactionIds.contains(order.transactionId())) return;
        String stockSymbol = order.instrumentId();
        Map<String, ConcurrentSkipListSet<TradeResponse>> queues = isBuy ? buyOrderQueues : sellOrderQueues;
        Comparator<TradeResponse> comparator = isBuy
                ? Comparator.comparingDouble(TradeResponse::getAskedPrice)
                : Comparator.comparingDouble(TradeResponse::getAskedPrice).reversed();

        ReadWriteLock lock = stockLocks.computeIfAbsent(stockSymbol, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            queues.computeIfAbsent(stockSymbol, k -> new ConcurrentSkipListSet<>(comparator))
                    .add(order);
            loadedTransactionIds.add(order.transactionId());
            orderLookup.put(order.transactionId(), order);
            logger.debug("Placed {} order for stock {}: {}", isBuy ? "buy" : "sell", stockSymbol, order);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void placeBuyOrder(TradeResponse order) {
        placeOrder(order, true);
    }

    public void placeSellOrder(TradeResponse order) {
        placeOrder(order, false);
    }

    private record OrderTask(TransactionCommand command) {}
}