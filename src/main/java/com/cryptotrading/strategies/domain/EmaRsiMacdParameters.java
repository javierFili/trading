package com.cryptotrading.strategies.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Configurable parameters of {@link EmaRsiMacdStrategy}. Defaults follow the project specification. */
public record EmaRsiMacdParameters(
        int emaFast,
        int emaSlow,
        int rsiPeriod,
        BigDecimal rsiMin,
        BigDecimal rsiMax,
        int macdFast,
        int macdSlow,
        int macdSignal,
        int atrPeriod,
        int volumePeriod,
        BigDecimal volumeFactor,
        BigDecimal stopAtrMultiplier,
        BigDecimal takeProfitAtrMultiplier,
        int lookback) {

    public static EmaRsiMacdParameters defaults() {
        return new EmaRsiMacdParameters(20, 50, 14, new BigDecimal("50"), new BigDecimal("70"), 12, 26, 9, 14, 20,
                new BigDecimal("1.0"), new BigDecimal("1.5"), new BigDecimal("3.0"), 300);
    }

    public EmaRsiMacdParameters {
        Objects.requireNonNull(rsiMin);
        Objects.requireNonNull(rsiMax);
        Objects.requireNonNull(volumeFactor);
        Objects.requireNonNull(stopAtrMultiplier);
        Objects.requireNonNull(takeProfitAtrMultiplier);
        if (emaFast >= emaSlow) {
            throw new IllegalArgumentException("emaFast must be < emaSlow");
        }
        if (rsiMin.compareTo(rsiMax) >= 0) {
            throw new IllegalArgumentException("rsiMin must be < rsiMax");
        }
        if (stopAtrMultiplier.signum() <= 0 || takeProfitAtrMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("ATR multipliers must be > 0");
        }
        int needed = Math.max(emaSlow, macdSlow + macdSignal) + 2;
        if (lookback < needed) {
            throw new IllegalArgumentException("lookback must be >= " + needed);
        }
    }

    public Map<String, String> asMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("emaFast", String.valueOf(emaFast));
        m.put("emaSlow", String.valueOf(emaSlow));
        m.put("rsiPeriod", String.valueOf(rsiPeriod));
        m.put("rsiMin", rsiMin.toPlainString());
        m.put("rsiMax", rsiMax.toPlainString());
        m.put("macdFast", String.valueOf(macdFast));
        m.put("macdSlow", String.valueOf(macdSlow));
        m.put("macdSignal", String.valueOf(macdSignal));
        m.put("atrPeriod", String.valueOf(atrPeriod));
        m.put("volumePeriod", String.valueOf(volumePeriod));
        m.put("volumeFactor", volumeFactor.toPlainString());
        m.put("stopAtrMultiplier", stopAtrMultiplier.toPlainString());
        m.put("takeProfitAtrMultiplier", takeProfitAtrMultiplier.toPlainString());
        m.put("lookback", String.valueOf(lookback));
        return m;
    }
}
