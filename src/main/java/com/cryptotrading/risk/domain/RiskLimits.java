package com.cryptotrading.risk.domain;

import java.math.BigDecimal;

import static com.cryptotrading.shared.domain.DecimalPolicy.requireNonNegative;
import static com.cryptotrading.shared.domain.DecimalPolicy.requirePositive;

/**
 * Hard risk limits, loaded from configuration only. No strategy, signal or AI component can change them.
 * All amounts are in the quote asset (USDT).
 *
 * @param maxRiskPerTrade     maximum loss if the stop is hit, including fees, slippage and stop-limit buffer
 * @param maxDailyLoss        realized loss per UTC day that blocks new entries and trips the circuit breaker
 * @param maxOpenPositions    maximum simultaneous open positions
 * @param minQuoteReserve     quote balance that must remain untouched after the entry order
 * @param maxPositionNotional maximum notional of a single position
 * @param minRewardToRisk     minimum (net of costs) reward / risk ratio
 */
public record RiskLimits(
        BigDecimal maxRiskPerTrade,
        BigDecimal maxDailyLoss,
        int maxOpenPositions,
        BigDecimal minQuoteReserve,
        BigDecimal maxPositionNotional,
        BigDecimal minRewardToRisk) {

    public RiskLimits {
        requirePositive(maxRiskPerTrade, "maxRiskPerTrade");
        requirePositive(maxDailyLoss, "maxDailyLoss");
        requireNonNegative(minQuoteReserve, "minQuoteReserve");
        requirePositive(maxPositionNotional, "maxPositionNotional");
        requireNonNegative(minRewardToRisk, "minRewardToRisk");
        if (maxOpenPositions < 1) {
            throw new IllegalArgumentException("maxOpenPositions must be >= 1");
        }
        if (maxRiskPerTrade.compareTo(maxDailyLoss) > 0) {
            throw new IllegalArgumentException("maxRiskPerTrade cannot exceed maxDailyLoss");
        }
    }
}
