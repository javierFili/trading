package com.cryptotrading.backtesting.domain;

import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.risk.domain.RiskLimits;
import com.cryptotrading.risk.domain.RiskManager;
import com.cryptotrading.strategies.domain.Strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Inputs of one simulation. Candles before {@code tradingStart} are only used as indicator warm-up.
 */
public record BacktestConfig(
        Strategy strategy,
        RiskManager riskManager,
        RiskLimits limits,
        SymbolRules rules,
        BigDecimal feeRate,
        BigDecimal slippageRate,
        BigDecimal stopLimitBuffer,
        BigDecimal initialCapital,
        Instant tradingStart,
        Instant tradingEnd) {

    public BacktestConfig {
        Objects.requireNonNull(strategy);
        Objects.requireNonNull(riskManager);
        Objects.requireNonNull(limits);
        Objects.requireNonNull(rules);
        Objects.requireNonNull(feeRate);
        Objects.requireNonNull(slippageRate);
        Objects.requireNonNull(stopLimitBuffer);
        Objects.requireNonNull(initialCapital);
        if (!tradingEnd.isAfter(tradingStart)) {
            throw new IllegalArgumentException("tradingEnd must be after tradingStart");
        }
    }
}
