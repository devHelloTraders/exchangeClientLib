package com.traders.exchange.infrastructure.twelvedata;

import com.traders.common.model.InstrumentDTO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TwelveDataExchangeResolver {
    private final Map<String, String> categoryMap = new ConcurrentHashMap<>();

    public TwelveDataExchangeResolver() {
        categoryMap.put("NSE_FUTSTK", "NSE_FNO");
        categoryMap.put("NSE_OPTSTK", "NSE_FNO");
        categoryMap.put("NSE_OPTIDX", "NSE_FNO");
        categoryMap.put("NSE_FUTIDX", "NSE_FNO");
        categoryMap.put("MCX_FUTCOM", "MCX_COMM");
        categoryMap.put("MCX_OPTFUT", "MCX_COMM");
    }

    public String resolveCategory(InstrumentDTO instrument) {
        String key = instrument.getExchange() + "_" + instrument.getInstrument_type();
        return categoryMap.getOrDefault(key, "UNKNOWN");
    }
}