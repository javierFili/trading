package com.cryptotrading.indicators.domain;

import com.cryptotrading.shared.domain.DecimalPolicy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Building blocks shared by indicators. Every function returns a list aligned with its input, holding
 * {@code null} where the value is not yet defined (warm-up).
 */
final class SeriesMath {

    static final MathContext MC = DecimalPolicy.CALCULATION;

    private SeriesMath() {
    }

    static List<BigDecimal> sma(List<BigDecimal> values, int period) {
        List<BigDecimal> out = nulls(values.size());
        BigDecimal divisor = BigDecimal.valueOf(period);
        BigDecimal window = BigDecimal.ZERO;
        int valid = 0;
        for (int i = 0; i < values.size(); i++) {
            BigDecimal v = values.get(i);
            if (v == null) {
                window = BigDecimal.ZERO;
                valid = 0;
                continue;
            }
            window = window.add(v);
            valid++;
            if (valid > period) {
                window = window.subtract(values.get(i - period));
            }
            if (valid >= period) {
                out.set(i, window.divide(divisor, MC));
            }
        }
        return out;
    }

    /** EMA seeded with the SMA of the first {@code period} defined values; k = 2 / (period + 1). */
    static List<BigDecimal> ema(List<BigDecimal> values, int period) {
        BigDecimal k = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1L), MC);
        return recursive(values, period, (prev, v) -> v.subtract(prev).multiply(k, MC).add(prev, MC));
    }

    /** Wilder smoothing (RMA): seeded with the SMA, then prev + (v - prev) / period. */
    static List<BigDecimal> wilder(List<BigDecimal> values, int period) {
        BigDecimal p = BigDecimal.valueOf(period);
        return recursive(values, period, (prev, v) -> prev.multiply(p.subtract(BigDecimal.ONE), MC).add(v, MC).divide(p, MC));
    }

    private interface Step {
        BigDecimal next(BigDecimal previous, BigDecimal value);
    }

    private static List<BigDecimal> recursive(List<BigDecimal> values, int period, Step step) {
        List<BigDecimal> out = nulls(values.size());
        int first = firstDefined(values);
        if (first < 0 || values.size() - first < period) {
            return out;
        }
        BigDecimal seed = BigDecimal.ZERO;
        for (int i = first; i < first + period; i++) {
            seed = seed.add(values.get(i));
        }
        BigDecimal prev = seed.divide(BigDecimal.valueOf(period), MC);
        out.set(first + period - 1, prev);
        for (int i = first + period; i < values.size(); i++) {
            prev = step.next(prev, values.get(i));
            out.set(i, prev);
        }
        return out;
    }

    static List<BigDecimal> subtract(List<BigDecimal> a, List<BigDecimal> b) {
        List<BigDecimal> out = nulls(a.size());
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != null && b.get(i) != null) {
                out.set(i, a.get(i).subtract(b.get(i), MC));
            }
        }
        return out;
    }

    static int firstDefined(List<BigDecimal> values) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != null) {
                return i;
            }
        }
        return -1;
    }

    static List<BigDecimal> nulls(int size) {
        return new ArrayList<>(Collections.nCopies(size, null));
    }
}
