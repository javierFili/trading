package com.cryptotrading.trading.domain;

import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.shared.domain.BusinessRuleException;
import com.cryptotrading.shared.domain.TradingMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeProposalTest {

    static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

    static TradeProposal proposal() {
        return TradeProposal.create(null, UUID.randomUUID(), BTC_USDT, null, ProposalPurpose.ENTRY, OrderSide.BUY,
                bd("64000"), bd("63040"), bd("66500"), bd("0.0004"), bd("25.6128"), bd("0.498"), bd("0.051"), bd("1.87"),
                List.of("reason"), NOW, Duration.ofMinutes(10), TradingMode.TESTNET);
    }

    @Test
    void approvalRequiresExactVersionAndHash() {
        TradeProposal p = proposal();
        assertThatThrownBy(() -> p.approve(p.version() + 1, p.contentHash(), NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("PROPOSAL_CHANGED"));
        assertThatThrownBy(() -> p.approve(p.version(), "0".repeat(64), NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("PROPOSAL_CHANGED"));
        p.approve(p.version(), p.contentHash(), NOW);
        assertThat(p.status()).isEqualTo(ProposalStatus.APPROVED);
    }

    @Test
    void expiredProposalCannotBeApproved() {
        TradeProposal p = proposal();
        assertThatThrownBy(() -> p.approve(p.version(), p.contentHash(), NOW.plus(Duration.ofMinutes(10))))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.kind()).isEqualTo(BusinessRuleException.Kind.GONE));
    }

    @Test
    void hashCoversEconomicContentAndIsScaleInsensitive() {
        TradeProposal p = proposal();
        assertThat(p.verifyIntegrity()).isTrue();
        // Same values with database scale (18 decimals) keep the same hash.
        TradeProposal reloaded = new TradeProposal(p.id(), null, p.accountId(), BTC_USDT, null, p.purpose(), p.side(),
                p.referencePrice().setScale(18), p.stopLoss().setScale(18), p.takeProfit().setScale(18),
                p.quantity().setScale(18), p.notional(), p.riskAmount(), p.estimatedFees(), p.rewardToRisk(),
                p.justification(), p.status(), null, p.contentHash(), p.expiresAt(), p.mode(), p.createdAt(), p.updatedAt(), 0);
        assertThat(reloaded.verifyIntegrity()).isTrue();
        // A tampered quantity is detected even if the version and hash sent by the client match.
        TradeProposal tampered = new TradeProposal(p.id(), null, p.accountId(), BTC_USDT, null, p.purpose(), p.side(),
                p.referencePrice(), p.stopLoss(), p.takeProfit(), bd("0.5"), p.notional(), p.riskAmount(),
                p.estimatedFees(), p.rewardToRisk(), p.justification(), p.status(), null, p.contentHash(), p.expiresAt(),
                p.mode(), p.createdAt(), p.updatedAt(), 0);
        assertThatThrownBy(() -> tampered.approve(0, p.contentHash(), NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("PROPOSAL_INTEGRITY"));
    }

    @Test
    void spotLongOnlyAndNoProposalsInBacktest() {
        assertThatThrownBy(() -> TradeProposal.create(null, UUID.randomUUID(), BTC_USDT, null, ProposalPurpose.ENTRY,
                OrderSide.SELL, bd("1"), null, null, bd("1"), bd("1"), null, bd("0"), null, List.of(), NOW,
                Duration.ofMinutes(1), TradingMode.TESTNET)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradeProposal.create(null, UUID.randomUUID(), BTC_USDT, null, ProposalPurpose.ENTRY,
                OrderSide.BUY, bd("1"), null, null, bd("1"), bd("1"), null, bd("0"), null, List.of(), NOW,
                Duration.ofMinutes(1), TradingMode.BACKTEST)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectedProposalCannotBeApprovedLater() {
        TradeProposal p = proposal();
        p.reject(p.version(), p.contentHash(), "no", NOW);
        assertThatThrownBy(() -> p.approve(p.version(), p.contentHash(), NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("INVALID_STATUS"));
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
