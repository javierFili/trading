package com.cryptotrading.indicators.domain;

import com.cryptotrading.market.domain.CandleSeries;
import com.cryptotrading.shared.domain.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Converts an aligned value list (with nulls during warm-up) into an {@link IndicatorSeries}. */
abstract class AbstractIndicator<V> implements Indicator<V> {

    protected abstract List<V> compute(List<Candle> candles);

    @Override
    public IndicatorSeries<V> calculate(CandleSeries series) {
        List<Candle> candles = series.candles();
        List<V> values = compute(candles);
        List<IndicatorPoint<V>> points = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            if (values.get(i) != null) {
                points.add(new IndicatorPoint<>(candles.get(i).openTime(), values.get(i)));
            }
        }
        return new IndicatorSeries<>(name(), points);
    }

    static List<BigDecimal> field(List<Candle> candles, Function<Candle, BigDecimal> getter) {
        return candles.stream().map(getter).toList();
    }

    static void requirePeriod(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1");
        }
    }
}
