package com.cryptotrading.shared.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import static com.cryptotrading.shared.domain.DecimalPolicy.requireNonNegative;
import static com.cryptotrading.shared.domain.DecimalPolicy.requirePositive;

/**
 * OHLCV candle. {@code openTime} is inclusive, {@code closeTime} is the last instant covered by the candle
 * (Binance convention: {@code openTime + interval - 1ms}). Whether the candle is <em>closed</em> depends on
 * the observation instant, see {@link #isClosedAt(Instant)}.
 */
public record Candle(
        Symbol symbol,
        Timeframe timeframe,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal quoteVolume,
        long tradeCount) {

    public Candle {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(closeTime, "closeTime");
        requirePositive(open, "open");
        requirePositive(high, "high");
        requirePositive(low, "low");
        requirePositive(close, "close");
        requireNonNegative(volume, "volume");
        requireNonNegative(quoteVolume, "quoteVolume");
        if (!closeTime.isAfter(openTime)) {
            throw new IllegalArgumentException("closeTime must be after openTime");
        }
        if (high.compareTo(low) < 0 || high.compareTo(open) < 0 || high.compareTo(close) < 0
                || low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("Inconsistent OHLC values for candle at " + openTime);
        }
        if (tradeCount < 0) {
            throw new IllegalArgumentException("tradeCount must be >= 0");
        }
    }

    /** A candle is closed (final) only once the observation instant is strictly after its close time. */
    public boolean isClosedAt(Instant observedAt) {
        return observedAt.isAfter(closeTime);
    }
}
