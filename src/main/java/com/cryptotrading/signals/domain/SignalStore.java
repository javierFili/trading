package com.cryptotrading.signals.domain;

import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.shared.domain.TradingMode;
import com.cryptotrading.strategies.domain.StrategyDescriptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SignalStore {

    /** Registers (or finds) the exact strategy version; returns its id. */
    long registerStrategy(StrategyDescriptor descriptor);

    /** @return false when a signal for the same strategy, pair, timeframe and candle already exists */
    boolean save(Signal signal, long strategyId, TradingMode mode);

    void saveIndicators(long strategyId, Symbol symbol, Timeframe timeframe, Instant candleOpenTime,
                        Map<String, BigDecimal> values);

    Optional<Signal> find(UUID id);

    List<Signal> recent(int limit);

    /** indicator name → (candle open time → value), for charting. */
    Map<String, Map<Instant, BigDecimal>> indicators(Symbol symbol, Timeframe timeframe, Instant from, Instant to);
}
