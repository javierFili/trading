package com.cryptotrading.indicators.domain;

import com.cryptotrading.shared.domain.Candle;

import java.math.BigDecimal;
import java.util.List;

/** MACD line = EMA(fast) − EMA(slow); signal = EMA(signal) of the MACD line; histogram = MACD − signal. */
public final class Macd extends AbstractIndicator<Macd.Value> {

    public record Value(BigDecimal macd, BigDecimal signal, BigDecimal histogram) {
    }

    private final int fast;
    private final int slow;
    private final int signal;

    public Macd(int fast, int slow, int signal) {
        requirePeriod(fast);
        requirePeriod(slow);
        requirePeriod(signal);
        if (fast >= slow) {
            throw new IllegalArgumentException("fast period must be < slow period");
        }
        this.fast = fast;
        this.slow = slow;
        this.signal = signal;
    }

    @Override
    public String name() {
        return "MACD(" + fast + "," + slow + "," + signal + ")";
    }

    @Override
    public int warmupPeriod() {
        return slow + signal - 1;
    }

    @Override
    protected List<Value> compute(List<Candle> candles) {
        List<BigDecimal> closes = field(candles, Candle::close);
        List<BigDecimal> line = SeriesMath.subtract(SeriesMath.ema(closes, fast), SeriesMath.ema(closes, slow));
        List<BigDecimal> signalLine = SeriesMath.ema(line, signal);
        List<Value> out = new java.util.ArrayList<>(java.util.Collections.nCopies(candles.size(), null));
        for (int i = 0; i < candles.size(); i++) {
            if (signalLine.get(i) != null) {
                out.set(i, new Value(line.get(i), signalLine.get(i), line.get(i).subtract(signalLine.get(i), SeriesMath.MC)));
            }
        }
        return out;
    }
}
