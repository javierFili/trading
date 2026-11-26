package com.cryptotrading.indicators.domain;

import java.time.Instant;
import java.util.Objects;

/** Indicator value computed with data up to and including the candle opened at {@code candleOpenTime}. */
public record IndicatorPoint<V>(Instant candleOpenTime, V value) {

    public IndicatorPoint {
        Objects.requireNonNull(candleOpenTime, "candleOpenTime");
        Objects.requireNonNull(value, "value");
    }
}
