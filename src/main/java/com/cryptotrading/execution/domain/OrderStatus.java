package com.cryptotrading.execution.domain;

import com.cryptotrading.exchange.domain.ExchangeOrderStatus;

/**
 * Local order status. PENDING_SUBMIT = persisted, about to be sent (or the process died before sending).
 * UNKNOWN = sent but the outcome is ambiguous; reconciliation is mandatory.
 */
public enum OrderStatus {
    PENDING_SUBMIT, UNKNOWN, NEW, PARTIALLY_FILLED, FILLED, CANCELED, REJECTED, EXPIRED;

    public boolean isFinal() {
        return this == FILLED || this == CANCELED || this == REJECTED || this == EXPIRED;
    }

    public static OrderStatus of(ExchangeOrderStatus status) {
        return OrderStatus.valueOf(status.name());
    }
}
