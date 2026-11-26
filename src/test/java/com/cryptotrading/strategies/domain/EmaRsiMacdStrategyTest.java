package com.cryptotrading.strategies.domain;

import com.cryptotrading.indicators.domain.Ema;
import com.cryptotrading.indicators.domain.Macd;
import com.cryptotrading.indicators.domain.Rsi;
import com.cryptotrading.market.domain.CandleSeries;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Timeframe;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;

public class EmaRsiMacdStrategyTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private final EmaRsiMacdStrategy strategy = new EmaRsiMacdStrategy(EmaRsiMacdParameters.defaults(), Timeframe.H1);

    /** Uptrend (+0.2/h) with a triangle-wave oscillation of period 48 and amplitude 12; rising volume on up legs. */
    public static List<Candle> wave(int n, String drift) {
        List<Candle> candles = new ArrayList<>();
        BigDecimal prev = null;
        for (int i = 0; i < n; i++) {
            int phase = i % 48;
            int tri = phase < 24 ? phase : 48 - phase;
            BigDecimal close = new BigDecimal("1000").add(new BigDecimal(drift).multiply(BigDecimal.valueOf(i)))
                    .add(BigDecimal.valueOf(tri).multiply(new BigDecimal("0.5")));
            BigDecimal open = prev == null ? close : prev;
            BigDecimal high = close.max(open).add(BigDecimal.ONE);
            BigDecimal low = close.min(open).subtract(BigDecimal.ONE);
            BigDecimal volume = BigDecimal.valueOf(phase < 24 ? 20 : 10);
            Instant t = T0.plus(Timeframe.H1.duration().multipliedBy(i));
            candles.add(new Candle(BTC_USDT, Timeframe.H1, t, t.plus(Timeframe.H1.duration()).minusMillis(1),
                    open, high, low, close, volume, volume.multiply(close), 1));
            prev = close;
        }
        return candles;
    }

    static CandleSeries window(List<Candle> all, int endInclusive, int size) {
        List<Candle> w = all.subList(endInclusive - size + 1, endInclusive + 1);
        return CandleSeries.of(BTC_USDT, Timeframe.H1, w.getLast().closeTime().plusMillis(1), w);
    }

    @Test
    void everyEntrySignalSatisfiesItsDocumentedConditions() {
        List<Candle> all = wave(900, "0.2");
        int entries = 0;
        int exits = 0;
        for (int i = 299; i < all.size(); i++) {
            CandleSeries s = window(all, i, 300);
            StrategyEvaluation e = strategy.evaluate(s);
            if (e.type() == SignalType.ENTRY_LONG) {
                entries++;
                assertThat(new Ema(20).calculate(s).latest().value()).isGreaterThan(new Ema(50).calculate(s).latest().value());
                var macd = new Macd(12, 26, 9).calculate(s);
                assertThat(macd.latest().value().histogram().signum()).isPositive();
                assertThat(macd.fromEnd(1).value().histogram().signum()).isLessThanOrEqualTo(0);
                BigDecimal rsi = new Rsi(14).calculate(s).latest().value();
                assertThat(rsi).isBetween(new BigDecimal("50"), new BigDecimal("70"));
                assertThat(e.suggestedStopLoss()).isLessThan(e.referencePrice());
                assertThat(e.suggestedTakeProfit()).isGreaterThan(e.referencePrice());
                assertThat(e.score().value()).isGreaterThanOrEqualTo(new BigDecimal("90"));
                assertThat(e.reasons()).isNotEmpty();
                assertThat(e.invalidationConditions()).isNotEmpty();
                assertThat(e.candleOpenTime()).isEqualTo(s.last().openTime());
            } else if (e.type() == SignalType.EXIT_LONG) {
                exits++;
                assertThat(e.suggestedStopLoss()).isNull();
            }
        }
        assertThat(entries).as("entry signals in synthetic wave").isPositive();
        assertThat(exits).as("exit signals in synthetic wave").isPositive();
    }

    @Test
    void steadyDowntrendNeverProducesEntries() {
        List<Candle> all = wave(700, "-0.5");
        for (int i = 299; i < all.size(); i++) {
            assertThat(strategy.evaluate(window(all, i, 300)).type()).isNotEqualTo(SignalType.ENTRY_LONG);
        }
    }

    @Test
    void insufficientHistoryIsNoAction() {
        List<Candle> all = wave(100, "0.2");
        StrategyEvaluation e = strategy.evaluate(window(all, 99, 100));
        assertThat(e.type()).isEqualTo(SignalType.NO_ACTION);
        assertThat(e.reasons().getFirst()).contains("insuficiente");
    }

    @Test
    void evaluationIsDeterministic() {
        CandleSeries s = window(wave(400, "0.2"), 399, 300);
        assertThat(strategy.evaluate(s)).isEqualTo(strategy.evaluate(s));
    }

    @Test
    void versionChangesWithParameters() {
        String base = strategy.descriptor().version();
        String other = StrategyFactory.create(EmaRsiMacdStrategy.CODE, Timeframe.H1, Map.of("rsiMax", "65"))
                .descriptor().version();
        assertThat(base).startsWith("1.0.0+");
        assertThat(other).isNotEqualTo(base);
        assertThat(StrategyFactory.create(EmaRsiMacdStrategy.CODE, Timeframe.H1, Map.of()).descriptor().version())
                .isEqualTo(base);
    }
}
