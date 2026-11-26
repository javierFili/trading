package com.cryptotrading.backtesting.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** A simulated round trip. {@code pnl} is net of entry and exit fees. */
public record BacktestTrade(Instant entryTime, BigDecimal entryPrice, Instant exitTime, BigDecimal exitPrice,
                            BigDecimal quantity, BigDecimal fees, BigDecimal pnl, ExitReason exitReason) {

    public enum ExitReason {
        STOP_LOSS, TAKE_PROFIT, SIGNAL, END
    }

    public BigDecimal entryNotional() {
        return entryPrice.multiply(quantity);
    }
}
