package com.cryptotrading.exchange.domain;

/** Exchange-neutral order status. Adapters map exchange-specific statuses onto these values. */
public enum ExchangeOrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED,
    EXPIRED;

    /** Final statuses never change again; non-final orders must keep being reconciled. */
    public boolean isFinal() {
        return this == FILLED || this == CANCELED || this == REJECTED || this == EXPIRED;
    }
}
