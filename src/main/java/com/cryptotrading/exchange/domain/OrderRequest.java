package com.cryptotrading.exchange.domain;

import com.cryptotrading.shared.domain.Symbol;

import java.math.BigDecimal;
import java.util.Objects;

import static com.cryptotrading.shared.domain.DecimalPolicy.requirePositive;

/**
 * A fully specified order, already rounded to the exchange filters. Building it does not send anything.
 *
 * @param price       limit price; {@code null} for MARKET
 * @param stopPrice   trigger price; only for stop/take-profit types
 * @param timeInForce required for LIMIT and stop-limit types
 */
public record OrderRequest(
        ClientOrderId clientOrderId,
        Symbol symbol,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal stopPrice,
        TimeInForce timeInForce) {

    public OrderRequest {
        Objects.requireNonNull(clientOrderId, "clientOrderId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        requirePositive(quantity, "quantity");
        if (type.requiresPrice()) {
            requirePositive(price, "price");
        } else if (price != null) {
            throw new IllegalArgumentException(type + " order must not have a price");
        }
        if (type.requiresStopPrice()) {
            requirePositive(stopPrice, "stopPrice");
        } else if (stopPrice != null) {
            throw new IllegalArgumentException(type + " order must not have a stopPrice");
        }
        if (type.requiresTimeInForce() && timeInForce == null) {
            throw new IllegalArgumentException(type + " order requires timeInForce");
        }
        if (!type.requiresTimeInForce() && timeInForce != null) {
            throw new IllegalArgumentException(type + " order must not have timeInForce");
        }
    }
}
