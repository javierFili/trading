package com.cryptotrading.strategies.domain;

import com.cryptotrading.shared.domain.Timeframe;

import java.util.Map;
import java.util.Objects;

/**
 * Identity and configuration of a strategy instance. {@code version} must change whenever logic or
 * parameters change, so every signal and backtest is traceable to the exact configuration that produced it.
 */
public record StrategyDescriptor(
        String id,
        String version,
        String description,
        Timeframe timeframe,
        Map<String, String> parameters) {

    public StrategyDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(timeframe, "timeframe");
        parameters = Map.copyOf(parameters);
    }
}
