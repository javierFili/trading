package com.cryptotrading.market.domain;

import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for candles. Inserts are idempotent: an existing (pair, timeframe, openTime) is ignored. */
public interface CandleStore {

    /** @return number of candles actually inserted */
    int saveAll(List<Candle> candles, String source);

    /** Ascending, openTime in {@code [fromInclusive, toExclusive)}. */
    List<Candle> findRange(Symbol symbol, Timeframe timeframe, Instant fromInclusive, Instant toExclusive);

    /** The last {@code count} candles closed at {@code asOf}, ascending. */
    List<Candle> findLatestClosed(Symbol symbol, Timeframe timeframe, Instant asOf, int count);

    Optional<Instant> latestOpenTime(Symbol symbol, Timeframe timeframe);
}
