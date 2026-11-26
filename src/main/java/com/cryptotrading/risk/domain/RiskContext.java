package com.cryptotrading.risk.domain;

import com.cryptotrading.exchange.domain.SymbolRules;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Snapshot of everything the risk manager needs, captured at {@code evaluatedAt}. Built by the application
 * layer from fresh data; the risk manager itself performs no I/O.
 *
 * @param lossToday       realized loss of the current UTC day as a positive number (0 if in profit)
 * @param feeRate         taker/maker fee per side as a fraction (0.001 = 0.1 %)
 * @param slippageRate    expected adverse slippage of market orders as a fraction of price
 * @param stopLimitBuffer distance of the stop-limit price below the stop trigger (worst-case stop fill)
 */
public record RiskContext(
        Instant evaluatedAt,
        BigDecimal freeQuoteBalance,
        int openPositions,
        BigDecimal lossToday,
        boolean circuitBreakerOpen,
        SymbolRules symbolRules,
        BigDecimal feeRate,
        BigDecimal slippageRate,
        BigDecimal stopLimitBuffer,
        Duration maxRulesAge) {

    public RiskContext {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(freeQuoteBalance, "freeQuoteBalance");
        Objects.requireNonNull(lossToday, "lossToday");
        Objects.requireNonNull(symbolRules, "symbolRules");
        Objects.requireNonNull(feeRate, "feeRate");
        Objects.requireNonNull(slippageRate, "slippageRate");
        Objects.requireNonNull(stopLimitBuffer, "stopLimitBuffer");
        Objects.requireNonNull(maxRulesAge, "maxRulesAge");
    }
}
