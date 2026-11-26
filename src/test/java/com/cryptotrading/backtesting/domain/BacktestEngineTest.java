package com.cryptotrading.backtesting.domain;

import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.market.domain.CandleSeries;
import com.cryptotrading.risk.domain.DefaultRiskManager;
import com.cryptotrading.risk.domain.RiskLimits;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.strategies.domain.ConfidenceScore;
import com.cryptotrading.strategies.domain.EmaRsiMacdParameters;
import com.cryptotrading.strategies.domain.EmaRsiMacdStrategy;
import com.cryptotrading.strategies.domain.EmaRsiMacdStrategyTest;
import com.cryptotrading.strategies.domain.SignalType;
import com.cryptotrading.strategies.domain.Strategy;
import com.cryptotrading.strategies.domain.StrategyDescriptor;
import com.cryptotrading.strategies.domain.StrategyEvaluation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;

class BacktestEngineTest {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    static final SymbolRules RULES = new SymbolRules(BTC_USDT, true, bd("0.01"), bd("0.01"), bd("1000000"),
            bd("0.00001"), bd("0.00001"), bd("9000"), bd("0"), bd("0"), bd("100"), bd("5"), null, T0);
    static final RiskLimits LIMITS = new RiskLimits(bd("0.50"), bd("2"), 1, bd("20"), bd("80"), bd("0"));

    /** Emits ENTRY_LONG (stop/tp given) when the last candle is index {@code entryAt}, EXIT_LONG at {@code exitAt}. */
    record Scripted(int entryAt, int exitAt, String stop, String tp, List<Instant> seenLast) implements Strategy {
        @Override
        public StrategyDescriptor descriptor() {
            return new StrategyDescriptor("SCRIPTED", "1", "test", Timeframe.H1, Map.of());
        }

        @Override
        public int requiredHistory() {
            return 2;
        }

        @Override
        public StrategyEvaluation evaluate(CandleSeries s) {
            seenLast.add(s.last().openTime());
            int idx = (int) java.time.Duration.between(T0, s.last().openTime()).toHours();
            SignalType type = idx == entryAt ? SignalType.ENTRY_LONG : idx == exitAt ? SignalType.EXIT_LONG : SignalType.NO_ACTION;
            return new StrategyEvaluation(type, s.last().openTime(), s.last().close(), new ConfidenceScore(BigDecimal.TEN),
                    Map.of(), List.of(), List.of(), type == SignalType.ENTRY_LONG ? bd(stop) : null,
                    type == SignalType.ENTRY_LONG ? bd(tp) : null);
        }
    }

    static Candle candle(int i, String open, String high, String low, String close) {
        Instant t = T0.plus(Timeframe.H1.duration().multipliedBy(i));
        return new Candle(BTC_USDT, Timeframe.H1, t, t.plus(Timeframe.H1.duration()).minusMillis(1),
                bd(open), bd(high), bd(low), bd(close), BigDecimal.ONE, BigDecimal.ONE, 1);
    }

    static List<Candle> flat(int n) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(candle(i, "1000", "1001", "999", "1000"));
        }
        return list;
    }

    static BacktestConfig config(Strategy s, List<Candle> candles) {
        return new BacktestConfig(s, new DefaultRiskManager(), LIMITS, RULES, bd("0.001"), bd("0.0005"), bd("0.002"),
                bd("100"), candles.getFirst().openTime(), candles.getLast().closeTime());
    }

    @Test
    void entryDecidedAtCloseIsFilledAtNextOpenWithSlippage() {
        List<Candle> candles = flat(10);
        candles.set(4, candle(4, "1010", "1012", "1005", "1008"));
        Scripted s = new Scripted(3, 6, "980", "1100", new ArrayList<>());

        BacktestResult r = new BacktestEngine().run(candles, config(s, candles));

        BacktestTrade t = r.trades().getFirst();
        assertThat(t.entryTime()).isEqualTo(candles.get(4).openTime());
        assertThat(t.entryPrice()).isEqualByComparingTo("1010.505"); // 1010 × 1.0005
        assertThat(t.exitReason()).isEqualTo(BacktestTrade.ExitReason.SIGNAL);
        assertThat(t.exitTime()).isEqualTo(candles.get(7).openTime());
        // The strategy never saw a candle beyond the one being evaluated.
        for (int i = 0; i < s.seenLast().size(); i++) {
            assertThat(s.seenLast().get(i)).isEqualTo(candles.get(i + 1).openTime());
        }
    }

    @Test
    void stopIsAssumedFirstWhenStopAndTargetAreTouchedInTheSameCandle() {
        List<Candle> candles = flat(10);
        candles.set(5, candle(5, "1000", "1200", "900", "1000"));
        BacktestResult r = new BacktestEngine().run(candles, config(new Scripted(3, -1, "980", "1100", new ArrayList<>()), candles));
        BacktestTrade t = r.trades().getFirst();
        assertThat(t.exitReason()).isEqualTo(BacktestTrade.ExitReason.STOP_LOSS);
        assertThat(t.exitPrice()).isEqualByComparingTo("979.51"); // 980 × (1 − 0.0005)
        assertThat(t.pnl().signum()).isNegative();
    }

    @Test
    void gapThroughStopFillsAtOpen() {
        List<Candle> candles = flat(10);
        candles.set(5, candle(5, "950", "960", "940", "955"));
        BacktestResult r = new BacktestEngine().run(candles, config(new Scripted(3, -1, "980", "1100", new ArrayList<>()), candles));
        assertThat(r.trades().getFirst().exitPrice()).isEqualByComparingTo("949.525"); // 950 × 0.9995
    }

    @Test
    void takeProfitFillsAtLimitPrice() {
        List<Candle> candles = flat(10);
        candles.set(6, candle(6, "1000", "1150", "995", "1120"));
        BacktestResult r = new BacktestEngine().run(candles, config(new Scripted(3, -1, "980", "1100", new ArrayList<>()), candles));
        BacktestTrade t = r.trades().getFirst();
        assertThat(t.exitReason()).isEqualTo(BacktestTrade.ExitReason.TAKE_PROFIT);
        assertThat(t.exitPrice()).isEqualByComparingTo("1100");
    }

    @Test
    void finalEquityEqualsCapitalPlusNetPnlAndFeesAreCharged() {
        List<Candle> all = EmaRsiMacdStrategyTest.wave(1500, "0.2");
        Strategy strategy = new EmaRsiMacdStrategy(new EmaRsiMacdParameters(20, 50, 14, bd("50"), bd("70"), 12, 26, 9,
                14, 20, bd("1.0"), bd("1.5"), bd("3.0"), 300), Timeframe.H1);
        BacktestConfig cfg = new BacktestConfig(strategy, new DefaultRiskManager(), LIMITS, RULES, bd("0.001"),
                bd("0.0005"), bd("0.002"), bd("100"), all.get(300).openTime(), all.getLast().closeTime());

        BacktestResult r = new BacktestEngine().run(all, cfg);

        assertThat(r.trades()).isNotEmpty();
        BigDecimal net = r.trades().stream().map(BacktestTrade::pnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(r.metrics().finalEquity()).isEqualByComparingTo(bd("100").add(net).setScale(8, java.math.RoundingMode.HALF_EVEN));
        assertThat(r.metrics().totalFees().signum()).isPositive();
        r.trades().forEach(t -> {
            assertThat(t.entryNotional()).isLessThanOrEqualTo(bd("80.1"));
            assertThat(t.entryTime()).isAfterOrEqualTo(all.get(300).openTime());
        });
    }

    @Test
    void metricsFromKnownTrades() {
        List<BacktestTrade> trades = List.of(trade("2"), trade("-1"), trade("1"));
        BacktestMetrics m = BacktestMetrics.compute(trades, bd("100"), bd("1.5"), bd("0.015"));
        assertThat(m.trades()).isEqualTo(3);
        assertThat(m.winRate()).isEqualByComparingTo("0.66666667");
        assertThat(m.netProfit()).isEqualByComparingTo("2");
        assertThat(m.profitFactor()).isEqualByComparingTo("3");
        assertThat(m.averageTrade()).isEqualByComparingTo("0.66666667");
        assertThat(m.realizedRewardToRisk()).isEqualByComparingTo("1.5");
        // returns 0.04, −0.02, 0.02 → mean 0.013333, sample std 0.030551 → 0.43643578
        assertThat(m.sharpePerTrade()).isEqualByComparingTo("0.43643578");
        assertThat(m.statisticallySignificant()).isFalse();
    }

    private static BacktestTrade trade(String pnl) {
        return new BacktestTrade(T0, bd("50"), T0.plusSeconds(3600), bd("50"), BigDecimal.ONE, bd("0.1"), bd(pnl),
                BacktestTrade.ExitReason.SIGNAL);
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
