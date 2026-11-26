package com.cryptotrading.market.domain;

import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static com.cryptotrading.shared.domain.CandleFixtures.hourly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandleSeriesTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-01T11:00:00Z");

    @Test
    void acceptsClosedAscendingCandles() {
        CandleSeries series = CandleSeries.of(BTC_USDT, Timeframe.H1, Instant.parse("2026-01-01T12:00:00Z"),
                List.of(hourly(T0, "100"), hourly(T1, "101")));
        assertThat(series.size()).isEqualTo(2);
        assertThat(series.last().openTime()).isEqualTo(T1);
    }

    @Test
    void rejectsCandleNotYetClosed_lookAheadProtection() {
        Instant duringSecondCandle = Instant.parse("2026-01-01T11:30:00Z");
        List<Candle> candles = List.of(hourly(T0, "100"), hourly(T1, "101"));
        assertThatThrownBy(() -> CandleSeries.of(BTC_USDT, Timeframe.H1, duringSecondCandle, candles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Look-ahead");
    }

    @Test
    void rejectsUnorderedOrDuplicatedCandles() {
        Instant asOf = Instant.parse("2026-01-02T00:00:00Z");
        assertThatThrownBy(() -> CandleSeries.of(BTC_USDT, Timeframe.H1, asOf,
                List.of(hourly(T1, "101"), hourly(T0, "100"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CandleSeries.of(BTC_USDT, Timeframe.H1, asOf,
                List.of(hourly(T0, "100"), hourly(T0, "100"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCandlesFromAnotherSymbol() {
        Instant asOf = Instant.parse("2026-01-02T00:00:00Z");
        assertThatThrownBy(() -> CandleSeries.of(Symbol.of("ETH", "USDT"), Timeframe.H1, asOf,
                List.of(hourly(T0, "100"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
