// com.traders.exchange.domain.OrderValidity
package com.traders.exchange.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum OrderValidity {
    INTRADAY, REGULAR
}