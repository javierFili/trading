package com.cryptotrading.risk.domain;

import com.cryptotrading.shared.domain.Symbol;

import java.math.BigDecimal;
import java.util.Objects;

import static com.cryptotrading.shared.domain.DecimalPolicy.requirePositive;

/** A long entry the system would like to open, before sizing. Spot only: stopLoss < entry < takeProfit. */
public record TradeIntent(Symbol symbol, BigDecimal entryPrice, BigDecimal stopLoss, BigDecimal takeProfit) {

    public TradeIntent {
        Objects.requireNonNull(symbol, "symbol");
        requirePositive(entryPrice, "entryPrice");
        requirePositive(stopLoss, "stopLoss");
        requirePositive(takeProfit, "takeProfit");
        if (stopLoss.compareTo(entryPrice) >= 0) {
            throw new IllegalArgumentException("Long stop loss must be below entry price");
        }
        if (takeProfit.compareTo(entryPrice) <= 0) {
            throw new IllegalArgumentException("Long take profit must be above entry price");
        }
    }
}
