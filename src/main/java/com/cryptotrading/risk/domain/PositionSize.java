package com.cryptotrading.risk.domain;

import java.math.BigDecimal;

/**
 * Result of position sizing, already rounded DOWN to the symbol's step size.
 *
 * @param quantity            base asset quantity
 * @param expectedEntryPrice  reference price adjusted for slippage
 * @param notional            quantity × expected entry price
 * @param riskAtStop          estimated loss if the stop is hit (fees, slippage and stop-limit buffer included)
 * @param estimatedFees       entry + exit fees (stop scenario)
 * @param rewardToRiskRatio   net reward at take profit / riskAtStop
 */
public record PositionSize(
        BigDecimal quantity,
        BigDecimal expectedEntryPrice,
        BigDecimal notional,
        BigDecimal riskAtStop,
        BigDecimal estimatedFees,
        BigDecimal rewardToRiskRatio) {
}
