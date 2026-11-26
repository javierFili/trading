package com.cryptotrading.exchange.domain;

import com.cryptotrading.shared.domain.Symbol;

import java.math.BigDecimal;
import java.util.Objects;

import static com.cryptotrading.shared.domain.DecimalPolicy.requirePositive;

/**
 * Protective SELL one-cancels-the-other order for a long position: a LIMIT_MAKER take profit above the
 * market and a STOP_LOSS_LIMIT below it. When one leg fills, the exchange cancels the other.
 */
public record OcoRequest(
        ClientOrderId listClientOrderId,
        ClientOrderId takeProfitClientOrderId,
        ClientOrderId stopLossClientOrderId,
        Symbol symbol,
        BigDecimal quantity,
        BigDecimal takeProfitPrice,
        BigDecimal stopTriggerPrice,
        BigDecimal stopLimitPrice) {

    public OcoRequest {
        Objects.requireNonNull(listClientOrderId, "listClientOrderId");
        Objects.requireNonNull(takeProfitClientOrderId, "takeProfitClientOrderId");
        Objects.requireNonNull(stopLossClientOrderId, "stopLossClientOrderId");
        Objects.requireNonNull(symbol, "symbol");
        requirePositive(quantity, "quantity");
        requirePositive(takeProfitPrice, "takeProfitPrice");
        requirePositive(stopTriggerPrice, "stopTriggerPrice");
        requirePositive(stopLimitPrice, "stopLimitPrice");
        if (takeProfitPrice.compareTo(stopTriggerPrice) <= 0) {
            throw new IllegalArgumentException("Take profit must be above the stop trigger");
        }
        if (stopLimitPrice.compareTo(stopTriggerPrice) > 0) {
            throw new IllegalArgumentException("Stop limit price must not be above the stop trigger for a SELL");
        }
    }
}
