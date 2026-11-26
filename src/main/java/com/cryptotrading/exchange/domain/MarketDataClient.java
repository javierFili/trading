package com.cryptotrading.exchange.domain;

import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Public market data (no credentials). */
public interface MarketDataClient {

    Instant serverTime();

    BigDecimal latestPrice(Symbol symbol);

    /**
     * Historical candles with {@code openTime} in {@code [startInclusive, endExclusive)}, ascending.
     * May include the current, still-open candle; callers filter with {@link Candle#isClosedAt(Instant)}.
     */
    List<Candle> candles(Symbol symbol, Timeframe timeframe, Instant startInclusive, Instant endExclusive, int limit);

    SymbolRules symbolRules(Symbol symbol);
}
