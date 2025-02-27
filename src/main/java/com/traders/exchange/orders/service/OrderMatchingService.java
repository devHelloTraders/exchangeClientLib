// com.traders.exchange.orders.service.OrderMatchingService
package com.traders.exchange.orders.service;

import com.traders.common.model.MarketQuotes;
import com.traders.exchange.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Modern OrderMatchingService with ideal buy/sell logic: buy at price <= askedPrice, sell at price >= askedPrice.
 */
@Service
public class OrderMatchingService implements OrderMatchingPort {
    private static final Logger logger = LoggerFactory.getLogger(OrderMatchingService.class);

    private final Map<String, PriorityQueue<TradeResponse>> buyOrderQueues = new ConcurrentHashMap<>();
    private final Map<String, PriorityQueue<TradeResponse>> sellOrderQueues = new ConcurrentHashMap<>();
    private final Set<Long> loadedTransactionIds = ConcurrentHashMap.newKeySet();
    private final Map<String, ReentrantLock> stockLocks = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<Double>> priceUpdateQueues = new ConcurrentHashMap<>();

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private final TradeFeignService tradeFeign;

    public OrderMatchingService(TradeFeignService tradeFeign) {
        this.tradeFeign = tradeFeign;
    }

    @Override
    public void executeTransaction(TransactionCommand command) {
        // Process orders synchronously to queue immediately
        switch (command) {
            case TransactionCommand.PlaceBuy(var resp) -> {
                try {

                    resp.request().orderCategory().validateTradeRequest( resp.request());
                    placeOrder(resp, true);
                    resp.request().orderCategory().postProcessOrder(this, resp);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid buy order request: {}", e.getMessage());
                }
            }
            case TransactionCommand.PlaceSell(var resp) -> {
                try {
                    resp.request().orderCategory().validateTradeRequest(resp.request());
                    placeOrder(resp, false);
                    resp.request().orderCategory().postProcessOrder(this, resp);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid sell order request: {}", e.getMessage());
                }
            }
            case TransactionCommand.UpdateStatus(var id, var status, var price) ->
                    tradeFeign.updateTradeTransaction(new TransactionUpdateRecord(id, price, status));
        }
    }

    @Override
    public void onPriceUpdate(String instrumentId, MarketQuotes quote) {
        if (quote.getLatestTradedPrice() == 0) return;
        BlockingQueue<Double> priceQueue = priceUpdateQueues.computeIfAbsent(instrumentId, k -> new LinkedBlockingQueue<>());
        priceQueue.offer(quote.getLatestTradedPrice());
        executor.execute(() -> processPriceUpdate(instrumentId));
    }

    private void processPriceUpdate(String stockSymbol) {
        ReentrantLock lock = stockLocks.computeIfAbsent(stockSymbol, k -> new ReentrantLock());
        BlockingQueue<Double> priceQueue = priceUpdateQueues.get(stockSymbol);
        lock.lock();
        try {
            while (!priceQueue.isEmpty()) {
                Double price = priceQueue.poll();
                processOrdersForPrice(stockSymbol, price);
            }
        } finally {
            lock.unlock();
        }
    }

    private void processOrdersForPrice(String stockSymbol, Double price) {
        processBuyOrders(stockSymbol, price);
        processSellOrders(stockSymbol, price);
    }

    private void processBuyOrders(String stockSymbol, Double price) {
        PriorityQueue<TradeResponse> buyOrders = buyOrderQueues.get(stockSymbol);
        if (buyOrders == null) return;

        while (!buyOrders.isEmpty()) {
            TradeResponse buyOrder = buyOrders.peek();
            TradeRequest request = buyOrder.request();
            boolean shouldMatch = shouldMatchOrder(request.orderCategory(), request.askedPrice(), request.stopLossPrice(), request.targetPrice(), price, true);

            if (shouldMatch) {
                buyOrders.poll();
                TransactionUpdateRecord updateRecord = new TransactionUpdateRecord(
                        buyOrder.transactionId(), price, TransactionStatus.COMPLETED
                );
                completeTransaction(updateRecord);
            } else {
                break;
            }
        }
    }

    private void processSellOrders(String stockSymbol, Double price) {
        PriorityQueue<TradeResponse> sellOrders = sellOrderQueues.get(stockSymbol);
        if (sellOrders == null) return;

        while (!sellOrders.isEmpty()) {
            TradeResponse sellOrder = sellOrders.peek();
            TradeRequest request = sellOrder.request();
            boolean shouldMatch = shouldMatchOrder(request.orderCategory(), request.askedPrice(), request.stopLossPrice(), request.targetPrice(), price, false);

            if (shouldMatch) {
                sellOrders.poll();
                TransactionUpdateRecord updateRecord = new TransactionUpdateRecord(
                        sellOrder.transactionId(), price, TransactionStatus.COMPLETED
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
            case LIMIT -> isBuy ? price <= askedPrice : price >= askedPrice; // Buy low, sell high
            case BRACKET_AT_MARKET -> isBuy
                    ? price <= askedPrice && (targetPrice == 0 || price <= targetPrice)
                    : price >= askedPrice && (targetPrice == 0 || price >= targetPrice);
            case BRACKET_AT_LIMIT -> isBuy ? price <= askedPrice : price >= askedPrice;
            case STOP_LOSS -> stopLossPrice >= price; // Stop-loss triggers when price drops
        };
    }

    private void completeTransaction(TransactionUpdateRecord order) {
        tradeFeign.updateTradeTransaction(order);
        loadedTransactionIds.remove(order.id());
        logger.debug("Completed transaction: {}", order);
    }

    private void placeOrder(TradeResponse order, boolean isBuy) {
        if (loadedTransactionIds.contains(order.transactionId())) return;
        String stockSymbol = order.request().stockId().toString();
        var comparison =Comparator.comparingDouble(TradeResponse::getAskedPrice);
        Map<String, PriorityQueue<TradeResponse>> queues = isBuy ? buyOrderQueues : sellOrderQueues;
        Comparator<TradeResponse> comparator = isBuy
                ? comparison // Ascending for buy
                :comparison.reversed(); // Descending for sell

        queues.computeIfAbsent(stockSymbol, k -> new PriorityQueue<>(comparator))
                .offer(order);
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
}