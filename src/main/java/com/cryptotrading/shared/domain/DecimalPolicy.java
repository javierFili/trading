package com.cryptotrading.shared.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Single source of truth for decimal arithmetic rules. Financial values are always {@link BigDecimal};
 * {@code float}/{@code double} are forbidden (enforced by architecture tests).
 *
 * <p>Intermediate calculations use {@link #CALCULATION} (34 significant digits). Rounding to exchange
 * precision (tick size / step size) happens only at the boundary, with an explicit {@link RoundingMode}
 * chosen so the result never exceeds a risk limit (quantities are always rounded DOWN).
 */
public final class DecimalPolicy {

    public static final MathContext CALCULATION = MathContext.DECIMAL128;

    /** Quantities are rounded towards zero so that position size never exceeds the risk budget. */
    public static final RoundingMode QUANTITY_ROUNDING = RoundingMode.DOWN;

    private DecimalPolicy() {
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static boolean isNonNegative(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    public static BigDecimal requirePositive(BigDecimal value, String name) {
        if (!isPositive(value)) {
            throw new IllegalArgumentException(name + " must be > 0, was " + value);
        }
        return value;
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String name) {
        if (!isNonNegative(value)) {
            throw new IllegalArgumentException(name + " must be >= 0, was " + value);
        }
        return value;
    }
}
