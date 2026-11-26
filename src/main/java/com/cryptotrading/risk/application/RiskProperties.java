package com.cryptotrading.risk.application;

import com.cryptotrading.risk.domain.RiskLimits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * {@code risk.*}. Immutable at runtime: limits can only change by editing configuration and restarting,
 * which is audited at startup. Defaults are the values confirmed for the experimental phase.
 */
@Validated
@ConfigurationProperties(prefix = "risk")
public record RiskProperties(
        @DecimalMin("0.01") @DefaultValue("0.50") BigDecimal maxRiskPerTrade,
        @DecimalMin("0.01") @DefaultValue("2.00") BigDecimal maxDailyLoss,
        @Min(1) @DefaultValue("1") int maxOpenPositions,
        @DecimalMin("0") @DefaultValue("20") BigDecimal minQuoteReserve,
        @DecimalMin("1") @DefaultValue("80") BigDecimal maxPositionNotional,
        @DecimalMin("0") @DefaultValue("1.5") BigDecimal minRewardToRisk,
        @DecimalMin("0") @DecimalMax("0.01") @DefaultValue("0.001") BigDecimal feeRate,
        @DecimalMin("0") @DecimalMax("0.01") @DefaultValue("0.0005") BigDecimal slippageRate,
        @DecimalMin("0.0001") @DecimalMax("0.02") @DefaultValue("0.002") BigDecimal stopLimitBuffer,
        @DefaultValue("24h") Duration maxSymbolRulesAge,
        @Min(1) @DefaultValue("3") int maxConsecutiveFailures,
        @DecimalMin("1") @DefaultValue("100") BigDecimal initialCapital) {

    public RiskLimits limits() {
        return new RiskLimits(maxRiskPerTrade, maxDailyLoss, maxOpenPositions, minQuoteReserve, maxPositionNotional,
                minRewardToRisk);
    }
}
