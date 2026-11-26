package com.cryptotrading.strategies.domain;

import com.cryptotrading.shared.domain.Timeframe;

import java.util.Map;

/** Creates strategy instances by code, e.g. for backtests with custom parameters. */
public final class StrategyFactory {

    private StrategyFactory() {
    }

    public static Strategy create(String code, Timeframe timeframe, Map<String, String> overrides) {
        if (!EmaRsiMacdStrategy.CODE.equals(code)) {
            throw new IllegalArgumentException("Unknown strategy: " + code);
        }
        Map<String, String> base = new java.util.LinkedHashMap<>(EmaRsiMacdParameters.defaults().asMap());
        overrides.forEach((k, v) -> {
            if (!base.containsKey(k)) {
                throw new IllegalArgumentException("Unknown parameter: " + k);
            }
            base.put(k, v);
        });
        return new EmaRsiMacdStrategy(new EmaRsiMacdParameters(
                Integer.parseInt(base.get("emaFast")), Integer.parseInt(base.get("emaSlow")),
                Integer.parseInt(base.get("rsiPeriod")), new java.math.BigDecimal(base.get("rsiMin")),
                new java.math.BigDecimal(base.get("rsiMax")), Integer.parseInt(base.get("macdFast")),
                Integer.parseInt(base.get("macdSlow")), Integer.parseInt(base.get("macdSignal")),
                Integer.parseInt(base.get("atrPeriod")), Integer.parseInt(base.get("volumePeriod")),
                new java.math.BigDecimal(base.get("volumeFactor")), new java.math.BigDecimal(base.get("stopAtrMultiplier")),
                new java.math.BigDecimal(base.get("takeProfitAtrMultiplier")), Integer.parseInt(base.get("lookback"))),
                timeframe);
    }
}
