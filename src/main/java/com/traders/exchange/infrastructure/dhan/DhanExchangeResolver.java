package com.traders.exchange.infrastructure.dhan;

import com.traders.common.model.InstrumentDTO;
import com.traders.common.model.InstrumentInfo;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DhanExchangeResolver {
    private final Map<String, String> categoryMap = new ConcurrentHashMap<>();

    public DhanExchangeResolver() {
        categoryMap.put("NSE_FUTSTK", "NSE_FNO");
        categoryMap.put("NSE_OPTSTK", "NSE_FNO");
        categoryMap.put("NSE_OPTIDX", "NSE_FNO");
        categoryMap.put("MCX_FUTCOM", "MCX_COMM");
        categoryMap.put("MCX_OPTFUT", "MCX_COMM");
    }

    public String resolveCategory(InstrumentDTO instrument) {
        String key = instrument.getExchangeSegment() + "_" + instrument.getInstrument_type();
        return categoryMap.getOrDefault(key, "UNKNOWN");
    }
}