package com.cryptotrading.indicators.domain;

import com.cryptotrading.shared.domain.Candle;

import java.math.BigDecimal;
import java.util.List;

/** Bollinger Bands: SMA(period) ± k · population standard deviation of closes. */
public final class BollingerBands extends AbstractIndicator<BollingerBands.Value> {

    public record Value(BigDecimal lower, BigDecimal middle, BigDecimal upper) {
    }

    private final int period;
    private final BigDecimal multiplier;

    public BollingerBands(int period, BigDecimal multiplier) {
        requirePeriod(period);
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public String name() {
        return "BB(" + period + "," + multiplier.toPlainString() + ")";
    }

    @Override
    public int warmupPeriod() {
        return period;
    }

    @Override
    protected List<Value> compute(List<Candle> candles) {
        List<BigDecimal> closes = field(candles, Candle::close);
        List<BigDecimal> mean = SeriesMath.sma(closes, period);
        List<Value> out = new java.util.ArrayList<>(java.util.Collections.nCopies(candles.size(), null));
        BigDecimal n = BigDecimal.valueOf(period);
        for (int i = period - 1; i < candles.size(); i++) {
            BigDecimal m = mean.get(i);
            BigDecimal sumSq = BigDecimal.ZERO;
            for (int j = i - period + 1; j <= i; j++) {
                BigDecimal d = closes.get(j).subtract(m, SeriesMath.MC);
                sumSq = sumSq.add(d.multiply(d, SeriesMath.MC), SeriesMath.MC);
            }
            BigDecimal std = sumSq.divide(n, SeriesMath.MC).sqrt(SeriesMath.MC);
            BigDecimal width = std.multiply(multiplier, SeriesMath.MC);
            out.set(i, new Value(m.subtract(width, SeriesMath.MC), m, m.add(width, SeriesMath.MC)));
        }
        return out;
    }
}
