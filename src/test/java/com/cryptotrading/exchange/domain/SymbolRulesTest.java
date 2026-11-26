package com.cryptotrading.exchange.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;

class SymbolRulesTest {

    static final SymbolRules BTC = new SymbolRules(BTC_USDT, true, bd("0.01"), bd("0.01"), bd("1000000"),
            bd("0.00001"), bd("0.00001"), bd("9000"), bd("0"), bd("0"), bd("100"), bd("5"), bd("9000000"),
            Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void quantityIsAlwaysRoundedDown() {
        assertThat(BTC.floorQuantity(bd("0.000789999"), false)).isEqualByComparingTo("0.00078");
        assertThat(BTC.floorQuantity(bd("0.00078"), false)).isEqualByComparingTo("0.00078");
        assertThat(BTC.floorQuantity(bd("0.000009"), false)).isEqualByComparingTo("0");
    }

    @Test
    void marketOrdersFallBackToLotSizeWhenMarketStepIsZero() {
        assertThat(BTC.floorQuantity(bd("0.000789999"), true)).isEqualByComparingTo("0.00078");
    }

    @Test
    void priceRoundingFollowsRequestedDirection() {
        assertThat(BTC.roundPrice(bd("64000.129"), RoundingMode.DOWN)).isEqualByComparingTo("64000.12");
        assertThat(BTC.roundPrice(bd("64000.121"), RoundingMode.UP)).isEqualByComparingTo("64000.13");
    }

    @Test
    void validOrderHasNoViolations() {
        assertThat(BTC.validate(OrderType.LIMIT, bd("0.00078"), bd("64000.12"), bd("64000.12"))).isEmpty();
    }

    @Test
    void allViolationsAreReported() {
        var violations = BTC.validate(OrderType.LIMIT, bd("0.000015"), bd("64000.125"), bd("64000.125"));
        assertThat(violations).anyMatch(v -> v.startsWith("LOT_SIZE") && v.contains("multiple"))
                .anyMatch(v -> v.startsWith("PRICE_FILTER"))
                .anyMatch(v -> v.startsWith("NOTIONAL"));
    }

    @Test
    void notionalAtExactMinimumIsAccepted() {
        assertThat(BTC.validate(OrderType.MARKET, bd("0.0001"), null, bd("50000"))).isEmpty();
        assertThat(BTC.validate(OrderType.MARKET, bd("0.00009"), null, bd("50000"))).anyMatch(v -> v.startsWith("NOTIONAL"));
    }

    @Test
    void staleness() {
        Instant now = Instant.parse("2026-01-02T00:00:01Z");
        assertThat(BTC.isStale(now, Duration.ofHours(24))).isTrue();
        assertThat(BTC.isStale(now, Duration.ofHours(25))).isFalse();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
