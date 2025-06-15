package com.traders.exchange.orders.service;

import com.traders.common.model.TradeOrderDetails;
import com.traders.exchange.config.FeignConfig;
import com.traders.exchange.domain.TradeRequest;
import com.traders.exchange.domain.TransactionUpdateRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@Service
@FeignClient(name="portfolioservice",url = "${gateway.url}",configuration = FeignConfig.class)
public interface TradeFeignService {

    @PostMapping("/api/portfolio/transactions/addTxn")
    List<TradeOrderDetails> addTradeTransaction(@RequestBody TradeRequest tradeRequest, @RequestHeader("X-Client-IP") String clientIp);

    @PostMapping("/api/portfolio/transactions/addTxn/{userId}")
    List<TradeOrderDetails> addTradeTransaction(@PathVariable Long userId,
                                                @RequestBody TradeRequest tradeRequest);


    @PostMapping("/api/portfolio/transactions/update")
    void updateTradeTransaction(@RequestBody TransactionUpdateRecord updateRecord);

}