package com.cryptotrading.exchange.domain;

import com.cryptotrading.shared.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot of an order as reported by the exchange. */
public record ExchangeOrder(
        String exchangeOrderId,
        ClientOrderId clientOrderId,
        Symbol symbol,
        OrderSide side,
        OrderType type,
        ExchangeOrderStatus status,
        BigDecimal originalQuantity,
        BigDecimal executedQuantity,
        BigDecimal cumulativeQuoteQuantity,
        BigDecimal price,
        Instant updatedAt) {
}
