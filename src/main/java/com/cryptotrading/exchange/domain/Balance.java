package com.cryptotrading.exchange.domain;

import java.math.BigDecimal;
import java.util.Objects;

import static com.cryptotrading.shared.domain.DecimalPolicy.requireNonNegative;

public record Balance(String asset, BigDecimal free, BigDecimal locked) {

    public Balance {
        Objects.requireNonNull(asset, "asset");
        requireNonNegative(free, "free");
        requireNonNegative(locked, "locked");
    }

    public BigDecimal total() {
        return free.add(locked);
    }
}
