package com.cryptotrading.market.domain;

import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;

import java.time.Instant;

/** Published after a newly closed candle has been persisted. {@code asOf} is the instant it became final. */
public record CandleClosedEvent(Symbol symbol, Timeframe timeframe, Instant candleOpenTime, Instant asOf) {
}
