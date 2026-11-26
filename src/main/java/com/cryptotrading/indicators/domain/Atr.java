package com.cryptotrading.indicators.domain;

import com.cryptotrading.shared.domain.Candle;

import java.math.BigDecimal;
import java.util.List;

/** Wilder's Average True Range. TR is defined from the second candle on; first ATR at index {@code period}. */
public final class Atr extends AbstractIndicator<BigDecimal> {

    private final int period;

    public Atr(int period) {
        requirePeriod(period);
        this.period = period;
    }

    @Override
    public String name() {
        return "ATR(" + period + ")";
    }

    @Override
    public int warmupPeriod() {
        return period + 1;
    }

    @Override
    protected List<BigDecimal> compute(List<Candle> candles) {
        List<BigDecimal> trueRange = SeriesMath.nulls(candles.size());
        for (int i = 1; i < candles.size(); i++) {
            Candle c = candles.get(i);
            BigDecimal prevClose = candles.get(i - 1).close();
            BigDecimal tr = c.high().subtract(c.low())
                    .max(c.high().subtract(prevClose).abs())
                    .max(c.low().subtract(prevClose).abs());
            trueRange.set(i, tr);
        }
        return SeriesMath.wilder(trueRange, period);
    }
}
