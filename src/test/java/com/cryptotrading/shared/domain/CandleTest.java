package com.cryptotrading.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandleTest {

    private static final Instant OPEN = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void candleIsClosedOnlyAfterItsCloseTime() {
        Candle candle = CandleFixtures.hourly(OPEN, "100");
        assertThat(candle.isClosedAt(Instant.parse("2026-01-01T10:59:59.999Z"))).isFalse();
        assertThat(candle.isClosedAt(Instant.parse("2026-01-01T11:00:00Z"))).isTrue();
    }

    @Test
    void rejectsHighBelowClose() {
        assertThatThrownBy(() -> new Candle(CandleFixtures.BTC_USDT, Timeframe.H1, OPEN, OPEN.plusSeconds(3599),
                bd("100"), bd("101"), bd("99"), bd("102"), BigDecimal.ONE, BigDecimal.ONE, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositivePrices() {
        assertThatThrownBy(() -> CandleFixtures.hourly(OPEN, "0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void symbolValidatesAssets() {
        assertThat(Symbol.of("BTC", "USDT")).hasToString("BTC/USDT");
        assertThatThrownBy(() -> Symbol.of("btc", "USDT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Symbol.of("USDT", "USDT")).isInstanceOf(IllegalArgumentException.class);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
