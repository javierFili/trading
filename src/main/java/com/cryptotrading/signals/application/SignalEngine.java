package com.cryptotrading.signals.application;

import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.signals.domain.Signal;

import java.time.Instant;
import java.util.List;

/**
 * Runs the enabled strategies for a symbol/timeframe on the candles closed at {@code asOf} and persists
 * the resulting signals. Idempotent: re-running for the same closed candle returns no new signals.
 */
public interface SignalEngine {

    List<Signal> evaluate(Symbol symbol, Timeframe timeframe, Instant asOf);
}
