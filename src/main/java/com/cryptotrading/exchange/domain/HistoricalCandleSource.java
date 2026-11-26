package com.cryptotrading.exchange.domain;

import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;

import java.time.Instant;
import java.util.List;

/**
 * Read-only source of historical candles for backtesting. Implemented against a market-data-only endpoint
 * that cannot accept orders, so it is available in every mode, including BACKTEST.
 */
public interface HistoricalCandleSource {

    /** Candles with openTime in {@code [startInclusive, endExclusive)}, ascending, at most {@code limit}. */
    List<Candle> candles(Symbol symbol, Timeframe timeframe, Instant startInclusive, Instant endExclusive, int limit);
}
