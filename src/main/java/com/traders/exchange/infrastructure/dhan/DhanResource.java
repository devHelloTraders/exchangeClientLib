package com.traders.exchange.infrastructure.dhan;

import com.traders.common.model.MarketDetailsRequest;
import com.traders.exchange.application.ExchangeFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dhan")
public class DhanResource {
    private final ExchangeFacade facade;

    public DhanResource(ExchangeFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@RequestBody MarketDetailsRequest request) {
        facade.subscribe( request);
        return ResponseEntity.ok().build();
    }
}