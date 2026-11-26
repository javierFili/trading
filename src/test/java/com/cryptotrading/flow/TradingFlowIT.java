package com.cryptotrading.flow;

import com.cryptotrading.AbstractIntegrationTest;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.execution.application.ExecutionService;
import com.cryptotrading.execution.application.ReconciliationService;
import com.cryptotrading.market.domain.CandleSeries;
import com.cryptotrading.market.domain.CandleStore;
import com.cryptotrading.portfolio.application.PortfolioService;
import com.cryptotrading.portfolio.domain.TradingPosition;
import com.cryptotrading.security.application.UserAccountService;
import com.cryptotrading.security.domain.Base32;
import com.cryptotrading.security.domain.Totp;
import com.cryptotrading.security.domain.UserRole;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.shared.domain.TradingMode;
import com.cryptotrading.signals.application.SignalEngine;
import com.cryptotrading.strategies.domain.EmaRsiMacdStrategyTest;
import com.cryptotrading.strategies.domain.SignalType;
import com.cryptotrading.strategies.domain.Strategy;
import com.cryptotrading.trading.domain.ProposalPurpose;
import com.cryptotrading.trading.domain.ProposalRepository;
import com.cryptotrading.trading.domain.ProposalStatus;
import com.cryptotrading.trading.domain.TradeProposal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end semi-automatic flow in TESTNET mode against an in-memory exchange:
 * closed candles → signal → risk-sized proposal → authenticated approval with TOTP → execution →
 * position + OCO protection → take profit → position closed, everything audited.
 */
@AutoConfigureMockMvc
@Import(TradingFlowIT.FakeExchangeConfig.class)
@TestPropertySource(properties = {
        "trading.mode=TESTNET",
        "binance.enabled=false",
        "risk.min-reward-to-risk=0",
        "market.sync-cron=-",
        "portfolio.snapshot-cron=-",
        "execution.reconcile-initial-delay=1h"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TradingFlowIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class FakeExchangeConfig {
        @Bean
        ExchangeClient fakeExchange() {
            return new FakeExchange();
        }

        @Bean
        @org.springframework.context.annotation.Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now());
        }
    }

    /** Test clock that can be advanced (TOTP codes are single-use per 30 s step). */
    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired ExchangeClient exchange;
    @Autowired CandleStore candles;
    @Autowired SignalEngine signals;
    @Autowired List<Strategy> strategies;
    @Autowired ProposalRepository proposals;
    @Autowired PortfolioService portfolio;
    @Autowired ExecutionService execution;
    @Autowired ReconciliationService reconciliation;
    @Autowired UserAccountService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static String token;
    static byte[] totpSecret;
    static long lastStep;

    FakeExchange fake() {
        return (FakeExchange) exchange;
    }

    @BeforeEach
    void enrolledTrader() throws Exception {
        if (token != null) {
            return;
        }
        users.createUser("trader1", "trader-password-123", UserRole.TRADER, "test");
        token = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "trader1", "password", "trader-password-123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asString();
        JsonNode setup = json.readTree(mvc.perform(post("/api/v1/auth/totp/setup").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        totpSecret = Base32.decode(setup.get("secret").asString());
        lastStep = Totp.step(clock.instant());
        mvc.perform(post("/api/v1/auth/totp/confirm").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + Totp.code(totpSecret, lastStep, 6) + "\"}"))
                .andExpect(status().isNoContent());
    }

    /** Each TOTP code is single-use: move to the next 30 s step and return its code. */
    static String nextTotp(MutableClock clock) {
        clock.advance(Duration.ofSeconds(Totp.STEP_SECONDS));
        lastStep = Totp.step(clock.instant());
        return Totp.code(totpSecret, lastStep, 6);
    }

    @Test
    @Order(1)
    void fullSemiAutomaticFlowWithTakeProfit() throws Exception {
        // 1. Closed candles whose last one produces an ENTRY_LONG signal.
        Instant asOf = storeCandlesUntilEntrySignal();
        Candle last = candles.findLatestClosed(BTC_USDT, Timeframe.H1, asOf, 1).getFirst();
        fake().setPrice(last.close());

        // 2. Signal engine → proposal (after commit), sized by the risk manager.
        assertThat(signals.evaluate(BTC_USDT, Timeframe.H1, asOf))
                .anyMatch(s -> s.evaluation().type() == SignalType.ENTRY_LONG);
        TradeProposal proposal = pending(ProposalPurpose.ENTRY);
        assertThat(proposal.mode()).isEqualTo(TradingMode.TESTNET);
        assertThat(proposal.riskAmount()).isLessThanOrEqualTo(new BigDecimal("0.50"));
        assertThat(proposal.notional()).isLessThanOrEqualTo(new BigDecimal("80"));
        assertThat(fake().submittedCount()).as("nothing is sent before approval").isZero();

        // 3. Wrong hash and missing 2FA are refused.
        approve(proposal, proposal.version(), "f".repeat(64), nextTotp(clock)).andExpect(status().isConflict());
        approve(proposal, proposal.version(), proposal.contentHash(), "000000").andExpect(status().isForbidden());

        // 4. Valid approval → asynchronous execution → position + OCO protection.
        approve(proposal, proposal.version(), proposal.contentHash(), nextTotp(clock)).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(15)).until(() -> proposalStatus(proposal.id()) == ProposalStatus.FILLED
                && portfolio.openPosition(ACCOUNT, BTC_USDT).map(TradingPosition::protectedByExchange).orElse(false));
        TradingPosition position = portfolio.openPosition(ACCOUNT, BTC_USDT).orElseThrow();
        assertThat(position.entryQuantity()).isLessThan(proposal.quantity()); // base commission deducted
        assertThat(fake().submittedCount()).isEqualTo(1);

        // 5. A second approval of the same proposal is impossible.
        approve(proposal, proposal.version(), proposal.contentHash(), nextTotp(clock)).andExpect(status().isConflict());

        // 6. Take profit hits → reconciliation closes the position with a net profit.
        fake().fillTakeProfit();
        reconciliation.reconcile();
        TradingPosition closed = portfolio.find(position.id()).orElseThrow();
        assertThat(closed.status()).isEqualTo(TradingPosition.Status.CLOSED);
        assertThat(closed.exitReason()).isEqualTo("TAKE_PROFIT");
        assertThat(closed.realizedPnl().signum()).isPositive();
        reconciliation.reconcile(); // stop leg EXPIRED: no alert because the position is closed
        assertThat(breakerOpen()).isFalse();

        // 7. Everything is in the audit log.
        assertThat(jdbc.queryForList("select action from audit_log", String.class))
                .contains("PROPOSAL_CREATED", "APPROVAL_FAILED", "APPROVAL_DENIED_2FA", "PROPOSAL_APPROVED", "ORDER_SUBMITTING", "ORDER_FILLED",
                        "POSITION_OPENED", "PROTECTION_ACCEPTED", "POSITION_CLOSED");
    }

    @Test
    @Order(2)
    void ambiguousSubmissionIsResolvedByReconciliationNotByRetry() {
        fake().reset();
        TradeProposal proposal = approvedEntry(fake().latestPrice(BTC_USDT));
        fake().setBehavior(FakeExchange.Behavior.UNKNOWN_BUT_EXECUTED);

        execution.execute(proposal.id());

        assertThat(proposalStatus(proposal.id())).as(reason(proposal.id())).isEqualTo(ProposalStatus.EXECUTING);
        assertThat(jdbc.queryForObject("select count(*) from trading_order where proposal_id = ? and status = 'UNKNOWN'",
                Integer.class, proposal.id())).isEqualTo(1);
        assertThat(fake().submittedCount()).as("never retried").isEqualTo(1);

        fake().setBehavior(FakeExchange.Behavior.NORMAL);
        reconciliation.reconcile();

        assertThat(proposalStatus(proposal.id())).isEqualTo(ProposalStatus.FILLED);
        TradingPosition position = portfolio.openPosition(ACCOUNT, BTC_USDT).orElseThrow();
        assertThat(position.protectedByExchange()).isTrue();
        assertThat(fake().submittedCount()).isEqualTo(1);
        // clean up: close via take profit
        fake().fillTakeProfit();
        reconciliation.reconcile();
        assertThat(portfolio.openPosition(ACCOUNT, BTC_USDT)).isEmpty();
    }

    @Test
    @Order(3)
    void priceMoveBeforeExecutionCancelsWithoutSendingAnything() {
        fake().reset();
        BigDecimal reference = fake().latestPrice(BTC_USDT);
        TradeProposal proposal = approvedEntry(reference);
        fake().setPrice(reference.multiply(new BigDecimal("1.01"))); // +1 % > 0.3 % tolerance

        execution.execute(proposal.id());

        assertThat(proposalStatus(proposal.id())).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(proposals.find(proposal.id()).orElseThrow().statusReason()).startsWith("PRICE_MOVED");
        assertThat(fake().submittedCount()).isZero();
    }

    @Test
    @Order(4)
    void rejectedOrderFailsTheProposalAndCountsTowardsTheCircuitBreaker() {
        fake().reset();
        TradeProposal proposal = approvedEntry(fake().latestPrice(BTC_USDT));
        fake().setBehavior(FakeExchange.Behavior.REJECT);

        execution.execute(proposal.id());

        assertThat(proposalStatus(proposal.id())).as(reason(proposal.id())).isEqualTo(ProposalStatus.FAILED);
        assertThat(jdbc.queryForObject("select consecutive_failures from circuit_breaker where account_id = ?",
                Integer.class, ACCOUNT)).isPositive();
        assertThat(portfolio.openPosition(ACCOUNT, BTC_USDT)).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    private Instant storeCandlesUntilEntrySignal() {
        List<Candle> wave = EmaRsiMacdStrategyTest.wave(900, "0.2");
        Strategy strategy = strategies.getFirst();
        for (int i = 299; i < wave.size(); i++) {
            List<Candle> window = wave.subList(i - 299, i + 1);
            Instant asOf = window.getLast().closeTime().plusMillis(1);
            if (strategy.evaluate(CandleSeries.of(BTC_USDT, Timeframe.H1, asOf, window)).type() == SignalType.ENTRY_LONG) {
                candles.saveAll(wave.subList(0, i + 1), "TEST");
                return asOf;
            }
        }
        throw new AssertionError("synthetic series has no entry signal");
    }

    private TradeProposal pending(ProposalPurpose purpose) {
        return proposals.findByStatus(List.of(ProposalStatus.PENDING_APPROVAL), 10).stream()
                .filter(p -> p.purpose() == purpose).findFirst().orElseThrow();
    }

    /** An ENTRY proposal already approved (the approval path itself is covered by the first test). */
    private TradeProposal approvedEntry(BigDecimal reference) {
        BigDecimal stop = reference.multiply(new BigDecimal("0.98"));
        BigDecimal target = reference.multiply(new BigDecimal("1.06"));
        TradeProposal p = TradeProposal.create(null, ACCOUNT, BTC_USDT, null, ProposalPurpose.ENTRY, OrderSide.BUY,
                reference, stop, target, new BigDecimal("0.05"), reference.multiply(new BigDecimal("0.05")),
                new BigDecimal("0.3"), new BigDecimal("0.01"), new BigDecimal("2"), List.of("test"), clock.instant(),
                Duration.ofMinutes(10), TradingMode.TESTNET);
        proposals.save(p);
        TradeProposal stored = proposals.find(p.id()).orElseThrow();
        stored.approve(stored.version(), stored.contentHash(), clock.instant());
        proposals.save(stored);
        return proposals.find(p.id()).orElseThrow();
    }

    private ProposalStatus proposalStatus(UUID id) {
        return proposals.find(id).orElseThrow().status();
    }

    private String reason(UUID id) {
        return String.valueOf(proposals.find(id).orElseThrow().statusReason());
    }

    private boolean breakerOpen() {
        return Boolean.TRUE.equals(jdbc.queryForObject("select is_open from circuit_breaker where account_id = ?", Boolean.class, ACCOUNT));
    }

    private org.springframework.test.web.servlet.ResultActions approve(TradeProposal p, long version, String hash, String totp)
            throws Exception {
        return mvc.perform(post("/api/v1/proposals/" + p.id() + "/approve").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version", version, "contentHash", hash, "totpCode", totp))));
    }
}
