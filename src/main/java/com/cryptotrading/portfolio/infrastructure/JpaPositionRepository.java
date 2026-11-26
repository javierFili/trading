package com.cryptotrading.portfolio.infrastructure;

import com.cryptotrading.market.infrastructure.persistence.PairRegistry;
import com.cryptotrading.portfolio.domain.PositionRepository;
import com.cryptotrading.portfolio.domain.TradingPosition;
import com.cryptotrading.shared.domain.Symbol;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPositionRepository implements PositionRepository {

    private final TradingPositionJpaRepository jpa;
    private final PairRegistry pairs;

    JpaPositionRepository(TradingPositionJpaRepository jpa, PairRegistry pairs) {
        this.jpa = jpa;
        this.pairs = pairs;
    }

    @Override
    @Transactional
    public void save(TradingPosition p) {
        TradingPositionEntity e = jpa.findById(p.id()).orElseGet(TradingPositionEntity::new);
        if (e.id == null) {
            e.id = p.id();
            e.accountId = p.accountId();
            e.pairId = pairs.idOf(p.symbol());
            e.entryQty = p.entryQuantity();
            e.avgEntryPrice = p.averageEntryPrice();
            e.entryQuote = p.entryQuote();
            e.stopLoss = p.stopLoss();
            e.takeProfit = p.takeProfit();
            e.openedAt = p.openedAt();
        } else if (e.version != p.version()) {
            throw new org.springframework.dao.OptimisticLockingFailureException("Position " + p.id() + " was modified concurrently");
        }
        e.status = p.status().name();
        e.exitQty = p.exitQuantity();
        e.avgExitPrice = p.averageExitPrice();
        e.exitQuote = p.exitQuote();
        e.feesQuote = p.feesQuote();
        e.realizedPnl = p.realizedPnl();
        e.exitReason = p.exitReason();
        e.protectedFlag = p.protectedByExchange();
        e.closedAt = p.closedAt();
        jpa.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradingPosition> find(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradingPosition> findOpen(UUID accountId, Symbol symbol) {
        return jpa.findByAccountIdAndPairIdAndStatus(accountId, pairs.idOf(symbol), "OPEN").map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public int countOpen(UUID accountId) {
        return jpa.countByAccountIdAndStatus(accountId, "OPEN");
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal realizedPnlSince(UUID accountId, Instant since) {
        return jpa.sumRealizedSince(accountId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TradingPosition> recent(UUID accountId, int limit) {
        return jpa.findByAccountIdOrderByOpenedAtDesc(accountId, PageRequest.of(0, limit)).stream().map(this::toDomain).toList();
    }

    private TradingPosition toDomain(TradingPositionEntity e) {
        return new TradingPosition(e.id, e.accountId, pairs.symbolOf(e.pairId), TradingPosition.Status.valueOf(e.status),
                e.entryQty, e.avgEntryPrice, e.entryQuote, e.exitQty, e.avgExitPrice, e.exitQuote, e.stopLoss,
                e.takeProfit, e.feesQuote, e.realizedPnl, e.exitReason, e.protectedFlag, e.openedAt, e.closedAt, e.version);
    }
}
