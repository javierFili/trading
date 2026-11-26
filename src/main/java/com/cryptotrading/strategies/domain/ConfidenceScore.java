package com.cryptotrading.strategies.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Heuristic score in [0, 100] describing how many / how strongly a strategy's conditions are met.
 *
 * <p><b>This is NOT a probability.</b> It is not statistically calibrated and must never be presented as
 * "chance of profit". Calibration would require out-of-sample validation (see docs/BACKTESTING.md).
 */
public record ConfidenceScore(BigDecimal value) {

    private static final BigDecimal MAX = BigDecimal.valueOf(100);

    public ConfidenceScore {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Score must be within [0, 100], was " + value);
        }
    }
}
