package com.cryptotrading.signals.domain;

import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.strategies.domain.StrategyEvaluation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted, actionable output of a strategy. Uniqueness: one signal per
 * (symbol, timeframe, strategyId, strategyVersion, candleOpenTime) — see {@link #deduplicationKey()}.
 */
public record Signal(
        UUID id,
        Symbol symbol,
        Timeframe timeframe,
        String strategyId,
        String strategyVersion,
        StrategyEvaluation evaluation,
        Instant createdAt) {

    public Signal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(strategyVersion, "strategyVersion");
        Objects.requireNonNull(evaluation, "evaluation");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public String deduplicationKey() {
        return String.join("|", symbol.toString(), timeframe.name(), strategyId, strategyVersion,
                evaluation.candleOpenTime().toString());
    }
}
