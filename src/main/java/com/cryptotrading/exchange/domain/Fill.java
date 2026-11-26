package com.cryptotrading.exchange.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** One trade (partial execution) of an order, including the commission actually charged. */
public record Fill(
        String tradeId,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal quoteQuantity,
        BigDecimal commission,
        String commissionAsset,
        Instant executedAt) {
}
