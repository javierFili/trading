package com.cryptotrading.indicators.domain;

import com.cryptotrading.shared.domain.Candle;

import java.math.BigDecimal;
import java.util.List;

/** Exponential moving average of the close price. */
public final class Ema extends AbstractIndicator<BigDecimal> {

    private final int period;

    public Ema(int period) {
        requirePeriod(period);
        this.period = period;
    }

    @Override
    public String name() {
        return "EMA(" + period + ")";
    }

    @Override
    public int warmupPeriod() {
        return period;
    }

    @Override
    protected List<BigDecimal> compute(List<Candle> candles) {
        return SeriesMath.ema(field(candles, Candle::close), period);
    }
}
