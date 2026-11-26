package com.cryptotrading.indicators.domain;

import com.cryptotrading.shared.domain.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Wilder's Relative Strength Index on closes, in [0, 100]. First value at index {@code period}. */
public final class Rsi extends AbstractIndicator<BigDecimal> {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final int period;

    public Rsi(int period) {
        requirePeriod(period);
        this.period = period;
    }

    @Override
    public String name() {
        return "RSI(" + period + ")";
    }

    @Override
    public int warmupPeriod() {
        return period + 1;
    }

    @Override
    protected List<BigDecimal> compute(List<Candle> candles) {
        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();
        gains.add(null);
        losses.add(null);
        for (int i = 1; i < candles.size(); i++) {
            BigDecimal change = candles.get(i).close().subtract(candles.get(i - 1).close());
            gains.add(change.signum() > 0 ? change : BigDecimal.ZERO);
            losses.add(change.signum() < 0 ? change.negate() : BigDecimal.ZERO);
        }
        List<BigDecimal> avgGain = SeriesMath.wilder(gains, period);
        List<BigDecimal> avgLoss = SeriesMath.wilder(losses, period);
        List<BigDecimal> out = SeriesMath.nulls(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            BigDecimal g = avgGain.get(i);
            BigDecimal l = avgLoss.get(i);
            if (g == null || l == null) {
                continue;
            }
            if (l.signum() == 0) {
                out.set(i, g.signum() == 0 ? BigDecimal.valueOf(50) : HUNDRED);
            } else {
                BigDecimal rs = g.divide(l, SeriesMath.MC);
                out.set(i, HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), SeriesMath.MC)));
            }
        }
        return out;
    }
}
