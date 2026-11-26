package com.cryptotrading.trading.infrastructure;

import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.market.infrastructure.persistence.PairRegistry;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.TradingMode;
import com.cryptotrading.trading.domain.ProposalPurpose;
import com.cryptotrading.trading.domain.ProposalRepository;
import com.cryptotrading.trading.domain.ProposalStatus;
import com.cryptotrading.trading.domain.TradeProposal;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProposalRepository implements ProposalRepository {

    private static final List<String> ACTIVE = List.of("PENDING_APPROVAL", "APPROVED", "EXECUTING", "PARTIALLY_FILLED");
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final TradeProposalJpaRepository jpa;
    private final PairRegistry pairs;
    private final JsonMapper json;

    JpaProposalRepository(TradeProposalJpaRepository jpa, PairRegistry pairs, JsonMapper json) {
        this.jpa = jpa;
        this.pairs = pairs;
        this.json = json;
    }

    @Override
    @Transactional
    public void save(TradeProposal p) {
        TradeProposalEntity e = jpa.findById(p.id()).orElse(null);
        if (e == null) {
            e = new TradeProposalEntity();
            e.id = p.id();
            e.signalId = p.signalId();
            e.accountId = p.accountId();
            e.pairId = pairs.idOf(p.symbol());
            e.positionId = p.positionId();
            e.purpose = p.purpose().name();
            e.side = p.side().name();
            e.referencePrice = p.referencePrice();
            e.stopLoss = p.stopLoss();
            e.takeProfit = p.takeProfit();
            e.quantity = p.quantity();
            e.notional = p.notional();
            e.riskAmount = p.riskAmount();
            e.estFees = p.estimatedFees();
            e.rewardRisk = p.rewardToRisk();
            e.justification = json.writeValueAsString(p.justification());
            e.contentHash = p.contentHash();
            e.expiresAt = p.expiresAt();
            e.tradingMode = p.mode().name();
            e.createdAt = p.createdAt();
        } else if (e.version != p.version()) {
            throw new OptimisticLockingFailureException("Proposal " + p.id() + " was modified concurrently");
        }
        e.status = p.status().name();
        e.statusReason = p.statusReason();
        e.updatedAt = p.updatedAt();
        jpa.saveAndFlush(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradeProposal> find(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TradeProposal> findByStatus(Collection<ProposalStatus> statuses, int limit) {
        return jpa.findByStatusInOrderByCreatedAtDesc(statuses.stream().map(Enum::name).toList(), PageRequest.of(0, limit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TradeProposal> findExpirable(Instant now) {
        return jpa.findByStatusInAndExpiresAtLessThanEqual(List.of("PENDING_APPROVAL", "APPROVED"), now)
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActive(UUID accountId, Symbol symbol, ProposalPurpose purpose) {
        return jpa.existsByAccountIdAndPairIdAndPurposeAndStatusIn(accountId, pairs.idOf(symbol), purpose.name(), ACTIVE);
    }

    private TradeProposal toDomain(TradeProposalEntity e) {
        return new TradeProposal(e.id, e.signalId, e.accountId, pairs.symbolOf(e.pairId), e.positionId,
                ProposalPurpose.valueOf(e.purpose), OrderSide.valueOf(e.side), e.referencePrice, e.stopLoss, e.takeProfit,
                e.quantity, e.notional, e.riskAmount, e.estFees, e.rewardRisk, json.readValue(e.justification, STRINGS),
                ProposalStatus.valueOf(e.status), e.statusReason, e.contentHash, e.expiresAt, TradingMode.valueOf(e.tradingMode),
                e.createdAt, e.updatedAt, e.version);
    }
}
