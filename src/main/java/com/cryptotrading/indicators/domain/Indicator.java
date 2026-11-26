package com.cryptotrading.indicators.domain;

import com.cryptotrading.market.domain.CandleSeries;

/**
 * Pure, deterministic technical indicator. Implementations use {@link java.math.BigDecimal} with
 * {@link com.cryptotrading.shared.domain.DecimalPolicy#CALCULATION}, hold no state between calls and only
 * read the supplied (closed-candle) series.
 *
 * @param <V> value type, e.g. {@code BigDecimal} for EMA/RSI/ATR or a record for MACD
 */
public interface Indicator<V> {

    /** Name including parameters, e.g. {@code EMA(20)}. */
    String name();

    /** Number of candles needed before the first value is produced. */
    int warmupPeriod();

    IndicatorSeries<V> calculate(CandleSeries series);
}
