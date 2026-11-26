package com.cryptotrading.market.domain;

import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, chronologically ordered series of <b>closed</b> candles for one symbol and timeframe, as seen
 * at instant {@code asOf}. This is the only market input strategies and indicators receive.
 *
 * <p>Look-ahead protection: construction fails if any candle was not yet closed at {@code asOf}. In a
 * backtest {@code asOf} is the simulated time, so a strategy can never observe future data.
 */
public final class CandleSeries {

    private final Symbol symbol;
    private final Timeframe timeframe;
    private final Instant asOf;
    private final List<Candle> candles;

    private CandleSeries(Symbol symbol, Timeframe timeframe, Instant asOf, List<Candle> candles) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.asOf = asOf;
        this.candles = candles;
    }

    public static CandleSeries of(Symbol symbol, Timeframe timeframe, Instant asOf, List<Candle> candles) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(asOf, "asOf");
        List<Candle> copy = List.copyOf(candles);
        Candle previous = null;
        for (Candle candle : copy) {
            if (!candle.symbol().equals(symbol) || candle.timeframe() != timeframe) {
                throw new IllegalArgumentException("Candle " + candle.openTime() + " does not belong to "
                        + symbol + " " + timeframe);
            }
            if (!candle.isClosedAt(asOf)) {
                throw new IllegalArgumentException("Look-ahead: candle " + candle.openTime()
                        + " is not closed at " + asOf);
            }
            if (previous != null && !candle.openTime().isAfter(previous.openTime())) {
                throw new IllegalArgumentException("Candles must be strictly ascending by openTime; found "
                        + candle.openTime() + " after " + previous.openTime());
            }
            previous = candle;
        }
        return new CandleSeries(symbol, timeframe, asOf, copy);
    }

    public Symbol symbol() {
        return symbol;
    }

    public Timeframe timeframe() {
        return timeframe;
    }

    public Instant asOf() {
        return asOf;
    }

    public List<Candle> candles() {
        return candles;
    }

    public int size() {
        return candles.size();
    }

    public boolean isEmpty() {
        return candles.isEmpty();
    }

    public Candle last() {
        if (candles.isEmpty()) {
            throw new IllegalStateException("Empty series");
        }
        return candles.getLast();
    }
}
