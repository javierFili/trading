package com.cryptotrading.strategies.infrastructure;

import com.cryptotrading.strategies.domain.EmaRsiMacdParameters;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/** {@code strategies.ema-rsi-macd.*}; every value has the specification default. */
@ConfigurationProperties(prefix = "strategies.ema-rsi-macd")
public record StrategyProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int emaFast,
        @DefaultValue("50") int emaSlow,
        @DefaultValue("14") int rsiPeriod,
        @DefaultValue("50") BigDecimal rsiMin,
        @DefaultValue("70") BigDecimal rsiMax,
        @DefaultValue("12") int macdFast,
        @DefaultValue("26") int macdSlow,
        @DefaultValue("9") int macdSignal,
        @DefaultValue("14") int atrPeriod,
        @DefaultValue("20") int volumePeriod,
        @DefaultValue("1.0") BigDecimal volumeFactor,
        @DefaultValue("1.5") BigDecimal stopAtrMultiplier,
        @DefaultValue("3.0") BigDecimal takeProfitAtrMultiplier,
        @DefaultValue("300") int lookback) {

    public EmaRsiMacdParameters toParameters() {
        return new EmaRsiMacdParameters(emaFast, emaSlow, rsiPeriod, rsiMin, rsiMax, macdFast, macdSlow, macdSignal,
                atrPeriod, volumePeriod, volumeFactor, stopAtrMultiplier, takeProfitAtrMultiplier, lookback);
    }
}
