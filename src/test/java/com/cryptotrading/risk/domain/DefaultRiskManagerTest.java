package com.cryptotrading.risk.domain;

import com.cryptotrading.exchange.domain.SymbolRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultRiskManagerTest {

    static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    static final SymbolRules RULES = new SymbolRules(BTC_USDT, true, bd("0.01"), bd("0.01"), bd("1000000"),
            bd("0.00001"), bd("0.00001"), bd("9000"), bd("0"), bd("0"), bd("100"), bd("5"), bd("9000000"),
            NOW.minus(Duration.ofMinutes(5)));
    static final RiskLimits LIMITS = new RiskLimits(bd("0.50"), bd("2.00"), 1, bd("20"), bd("80"), bd("1.5"));

    private final RiskManager risk = new DefaultRiskManager();

    static RiskContext context(String free, int open, String lossToday, boolean breaker) {
        return new RiskContext(NOW, bd(free), open, bd(lossToday), breaker, RULES, bd("0.001"), bd("0.0005"),
                bd("0.002"), Duration.ofHours(24));
    }

    @Test
    void sizesByRiskIncludingFeesSlippageAndStopBuffer() {
        // e = 64000·1.0005 = 64032 ; x = 63040·0.998 = 62913.92
        // lossPerUnit = 1118.08 + 64.032 + 62.91392 = 1245.02592 → 0.5 / 1245.02592 = 0.0004016 → floor 0.0004
        // reward = 66500 − 64032 − 64.032 − 66.5 = 2337.468 → R/R = 1.8774
        TradeIntent intent = new TradeIntent(BTC_USDT, bd("64000"), bd("63040"), bd("66500"));

        RiskDecision decision = risk.evaluate(intent, LIMITS, context("100", 0, "0", false));

        assertThat(decision).isInstanceOfSatisfying(RiskDecision.Approved.class, a -> {
            PositionSize s = a.size();
            assertThat(s.quantity()).isEqualByComparingTo("0.0004");
            assertThat(s.expectedEntryPrice()).isEqualByComparingTo("64032");
            assertThat(s.notional()).isEqualByComparingTo("25.6128");
            assertThat(s.riskAtStop()).isEqualByComparingTo("0.49801037");
            assertThat(s.riskAtStop()).isLessThanOrEqualTo(LIMITS.maxRiskPerTrade());
            assertThat(s.rewardToRiskRatio()).isEqualByComparingTo("1.8774");
        });
    }

    @Test
    void positionIsCappedByMaxNotionalNeverEnlarged() {
        // Very tight stop → risk alone would allow a huge position; capped at 80 USDT notional.
        TradeIntent intent = new TradeIntent(BTC_USDT, bd("64000"), bd("63990"), bd("70000"));
        RiskDecision decision = risk.evaluate(intent, new RiskLimits(bd("0.50"), bd("2"), 1, bd("20"), bd("80"), bd("0")),
                new RiskContext(NOW, bd("100"), 0, bd("0"), false, RULES, bd("0.001"), bd("0.0005"), bd("0.0001"), Duration.ofHours(24)));
        assertThat(decision).isInstanceOfSatisfying(RiskDecision.Approved.class,
                a -> assertThat(a.size().notional()).isLessThanOrEqualTo(bd("80")));
    }

    @Test
    void reserveIsRespected() {
        TradeIntent intent = new TradeIntent(BTC_USDT, bd("64000"), bd("63040"), bd("66500"));
        RiskDecision decision = risk.evaluate(intent, LIMITS, context("30", 0, "0", false));
        assertThat(decision).isInstanceOfSatisfying(RiskDecision.Approved.class,
                a -> assertThat(bd("30").subtract(a.size().notional())).isGreaterThanOrEqualTo(bd("20")));
        assertThat(risk.evaluate(intent, LIMITS, context("20", 0, "0", false)))
                .isInstanceOfSatisfying(RiskDecision.Rejected.class,
                        r -> assertThat(r.violations()).anyMatch(v -> v.rule().equals("MIN_RESERVE")));
    }

    @Test
    void costsCanMakeRewardToRiskInsufficient() {
        // Gross R/R = 2 (stop 1.5 ATR, target 3 ATR) but costs reduce it to ≈1.41 < 1.5.
        TradeIntent intent = new TradeIntent(BTC_USDT, bd("64000"), bd("63040"), bd("65920"));
        assertThat(risk.evaluate(intent, LIMITS, context("100", 0, "0", false)))
                .isInstanceOfSatisfying(RiskDecision.Rejected.class,
                        r -> assertThat(r.violations()).extracting(RiskDecision.RiskViolation::rule).contains("MIN_REWARD_TO_RISK"));
    }

    @Test
    void dailyLossLimitBlocksAndReducesBudget() {
        TradeIntent intent = new TradeIntent(BTC_USDT, bd("64000"), bd("63040"), bd("66500"));
        assertThat(risk.evaluate(intent, LIMITS, context("100", 0, "2.00", false)))
                .isInstanceOfSatisfying(RiskDecision.Rejected.class,
                        r -> assertThat(r.violations()).extracting(RiskDecision.RiskViolation::rule).contains("DAILY_LOSS_LIMIT"));
        // 1.80 lost → only 0.20 left of daily budget, below the 0.50 per-trade limit.
        assertThat(risk.evaluate(intent, LIMITS, context("100", 0, "1.80", false)))
                .isInstanceOfSatisfying(RiskDecision.Approved.class,
                        a -> assertThat(a.size().riskAtStop()).isLessThanOrEqualTo(bd("0.20")));
    }

    @Test
    void allViolationsAreReportedTogether() {
        TradeIntent intent = new TradeIntent(BTC_USDT, bd("64000"), bd("63040"), bd("66500"));
        RiskDecision decision = risk.evaluate(intent, LIMITS, context("100", 1, "0", true));
        assertThat(decision).isInstanceOfSatisfying(RiskDecision.Rejected.class, r ->
                assertThat(r.violations()).extracting(RiskDecision.RiskViolation::rule)
                        .contains("CIRCUIT_BREAKER_OPEN", "MAX_OPEN_POSITIONS"));
    }

    @Test
    void staleRulesAreRejected() {
        TradeIntent intent = new TradeIntent(BTC_USDT, bd("64000"), bd("63040"), bd("66500"));
        RiskContext ctx = new RiskContext(NOW.plus(Duration.ofDays(2)), bd("100"), 0, bd("0"), false, RULES,
                bd("0.001"), bd("0.0005"), bd("0.002"), Duration.ofHours(24));
        assertThat(risk.evaluate(intent, LIMITS, ctx)).isInstanceOfSatisfying(RiskDecision.Rejected.class,
                r -> assertThat(r.violations()).extracting(RiskDecision.RiskViolation::rule).contains("SYMBOL_RULES_STALE"));
    }

    @Test
    void positionBelowExchangeMinimumIsRejectedNotEnlarged() {
        // Wide stop (20 %) → 0.5 USDT risk buys < 5 USDT notional.
        TradeIntent intent = new TradeIntent(BTC_USDT, bd("64000"), bd("51200"), bd("120000"));
        assertThat(risk.evaluate(intent, LIMITS, context("100", 0, "0", false)))
                .isInstanceOfSatisfying(RiskDecision.Rejected.class,
                        r -> assertThat(r.violations()).anyMatch(v -> v.detail().startsWith("NOTIONAL")));
    }

    @Test
    void invalidLongIntentIsRefusedAtConstruction() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TradeIntent(BTC_USDT, bd("100"), bd("101"), bd("110")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
