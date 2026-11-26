package com.cryptotrading.exchange.domain;

/** Spot order types supported by the platform. No margin/futures types exist by design. */
public enum OrderType {
    MARKET,
    LIMIT,
    LIMIT_MAKER,
    STOP_LOSS_LIMIT,
    TAKE_PROFIT_LIMIT;

    public boolean requiresPrice() {
        return this != MARKET;
    }

    public boolean requiresStopPrice() {
        return this == STOP_LOSS_LIMIT || this == TAKE_PROFIT_LIMIT;
    }

    public boolean requiresTimeInForce() {
        return this == LIMIT || this == STOP_LOSS_LIMIT || this == TAKE_PROFIT_LIMIT;
    }
}
