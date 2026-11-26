package com.cryptotrading.indicators.domain;

import com.cryptotrading.shared.domain.Candle;

import java.math.BigDecimal;
import java.util.List;

/** Simple moving average of base-asset volume. */
public final class VolumeSma extends AbstractIndicator<BigDecimal> {

    private final int period;

    public VolumeSma(int period) {
        requirePeriod(period);
        this.period = period;
    }

    @Override
    public String name() {
        return "VOLUME_SMA(" + period + ")";
    }

    @Override
    public int warmupPeriod() {
        return period;
    }

    @Override
    protected List<BigDecimal> compute(List<Candle> candles) {
        return SeriesMath.sma(field(candles, Candle::volume), period);
    }
}
