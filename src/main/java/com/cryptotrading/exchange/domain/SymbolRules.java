package com.cryptotrading.exchange.domain;

import com.cryptotrading.shared.domain.Symbol;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Trading rules of a symbol (exchange filters PRICE_FILTER, LOT_SIZE, MARKET_LOT_SIZE, NOTIONAL).
 * A {@code null} or zero value means "no constraint" (Binance uses 0 to disable a bound).
 *
 * @param fetchedAt rules are cached; they must be refreshed periodically and before orders
 */
public record SymbolRules(
        Symbol symbol,
        boolean tradingEnabled,
        BigDecimal tickSize,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal stepSize,
        BigDecimal minQuantity,
        BigDecimal maxQuantity,
        BigDecimal marketStepSize,
        BigDecimal marketMinQuantity,
        BigDecimal marketMaxQuantity,
        BigDecimal minNotional,
        BigDecimal maxNotional,
        Instant fetchedAt) {

    public SymbolRules {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
    }

    public SymbolRules withFetchedAt(Instant instant) {
        return new SymbolRules(symbol, tradingEnabled, tickSize, minPrice, maxPrice, stepSize, minQuantity, maxQuantity,
                marketStepSize, marketMinQuantity, marketMaxQuantity, minNotional, maxNotional, instant);
    }

    public boolean isStale(Instant now, Duration maxAge) {
        return fetchedAt.plus(maxAge).isBefore(now);
    }

    /** Rounds a quantity DOWN to the applicable step size (never increases exposure). */
    public BigDecimal floorQuantity(BigDecimal quantity, boolean marketOrder) {
        BigDecimal step = marketOrder && active(marketStepSize) ? marketStepSize : stepSize;
        return floorToIncrement(quantity, step);
    }

    /** Rounds a price to the tick size with the given mode (callers choose the conservative direction). */
    public BigDecimal roundPrice(BigDecimal price, RoundingMode mode) {
        if (!active(tickSize)) {
            return price;
        }
        BigDecimal ticks = price.divide(tickSize, 0, mode);
        return ticks.multiply(tickSize).setScale(scaleOf(tickSize), RoundingMode.UNNECESSARY);
    }

    /**
     * Validates an order against every filter and returns ALL violations (empty = valid).
     *
     * @param referencePrice price used for the notional check (limit price, or last price for MARKET)
     */
    public List<String> validate(OrderType type, BigDecimal quantity, BigDecimal limitPrice, BigDecimal referencePrice) {
        List<String> violations = new ArrayList<>();
        if (!tradingEnabled) {
            violations.add("TRADING_DISABLED: symbol " + symbol + " is not trading");
        }
        boolean market = type == OrderType.MARKET;
        BigDecimal step = market && active(marketStepSize) ? marketStepSize : stepSize;
        BigDecimal minQty = market && active(marketMinQuantity) ? marketMinQuantity : minQuantity;
        BigDecimal maxQty = market && active(marketMaxQuantity) ? marketMaxQuantity : maxQuantity;
        if (active(minQty) && quantity.compareTo(minQty) < 0) {
            violations.add("LOT_SIZE: quantity " + quantity.toPlainString() + " < min " + minQty.toPlainString());
        }
        if (active(maxQty) && quantity.compareTo(maxQty) > 0) {
            violations.add("LOT_SIZE: quantity " + quantity.toPlainString() + " > max " + maxQty.toPlainString());
        }
        if (active(step) && !isMultiple(quantity, step)) {
            violations.add("LOT_SIZE: quantity " + quantity.toPlainString() + " is not a multiple of " + step.toPlainString());
        }
        if (limitPrice != null) {
            if (active(minPrice) && limitPrice.compareTo(minPrice) < 0) {
                violations.add("PRICE_FILTER: price below min " + minPrice.toPlainString());
            }
            if (active(maxPrice) && limitPrice.compareTo(maxPrice) > 0) {
                violations.add("PRICE_FILTER: price above max " + maxPrice.toPlainString());
            }
            if (active(tickSize) && !isMultiple(limitPrice, tickSize)) {
                violations.add("PRICE_FILTER: price " + limitPrice.toPlainString() + " is not a multiple of " + tickSize.toPlainString());
            }
        }
        BigDecimal notional = quantity.multiply(referencePrice);
        if (active(minNotional) && notional.compareTo(minNotional) < 0) {
            violations.add("NOTIONAL: " + notional.toPlainString() + " < min " + minNotional.toPlainString());
        }
        if (active(maxNotional) && notional.compareTo(maxNotional) > 0) {
            violations.add("NOTIONAL: " + notional.toPlainString() + " > max " + maxNotional.toPlainString());
        }
        return violations;
    }

    static BigDecimal floorToIncrement(BigDecimal value, BigDecimal increment) {
        if (!active(increment)) {
            return value;
        }
        BigDecimal units = value.divide(increment, 0, RoundingMode.DOWN);
        return units.multiply(increment).setScale(scaleOf(increment), RoundingMode.UNNECESSARY);
    }

    private static boolean isMultiple(BigDecimal value, BigDecimal increment) {
        return value.remainder(increment).signum() == 0;
    }

    private static boolean active(BigDecimal bound) {
        return bound != null && bound.signum() > 0;
    }

    private static int scaleOf(BigDecimal increment) {
        return Math.max(increment.stripTrailingZeros().scale(), 0);
    }
}
