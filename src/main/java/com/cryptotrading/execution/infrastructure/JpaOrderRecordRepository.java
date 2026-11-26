package com.cryptotrading.execution.infrastructure;

import com.cryptotrading.exchange.domain.ClientOrderId;
import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.exchange.domain.OrderType;
import com.cryptotrading.execution.domain.OrderRecord;
import com.cryptotrading.execution.domain.OrderRecordRepository;
import com.cryptotrading.execution.domain.OrderRole;
import com.cryptotrading.execution.domain.OrderStatus;
import com.cryptotrading.market.infrastructure.persistence.PairRegistry;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaOrderRecordRepository implements OrderRecordRepository {

    private final TradingOrderJpaRepository jpa;
    private final PairRegistry pairs;

    JpaOrderRecordRepository(TradingOrderJpaRepository jpa, PairRegistry pairs) {
        this.jpa = jpa;
        this.pairs = pairs;
    }

    @Override
    @Transactional
    public void save(OrderRecord o) {
        TradingOrderEntity e = jpa.findById(o.id()).orElse(null);
        if (e == null) {
            e = new TradingOrderEntity();
            e.id = o.id();
            e.proposalId = o.proposalId();
            e.accountId = o.accountId();
            e.pairId = pairs.idOf(o.symbol());
            e.role = o.role().name();
            e.clientOrderId = o.clientOrderId().value();
            e.listClientOrderId = o.listClientOrderId() == null ? null : o.listClientOrderId().value();
            e.side = o.side().name();
            e.type = o.type().name();
            e.quantity = o.quantity();
            e.price = o.price();
            e.stopPrice = o.stopPrice();
            e.createdAt = o.createdAt();
        } else if (e.version != o.version()) {
            throw new OptimisticLockingFailureException("Order " + o.id() + " was modified concurrently");
        }
        e.positionId = o.positionId();
        e.exchangeOrderId = o.exchangeOrderId();
        e.status = o.status().name();
        e.executedQty = o.executedQuantity();
        e.cumulativeQuoteQty = o.cumulativeQuoteQuantity();
        e.statusReason = o.statusReason() == null || o.statusReason().length() <= 500 ? o.statusReason() : o.statusReason().substring(0, 500);
        e.fillsRecorded = o.fillsRecorded();
        e.updatedAt = o.updatedAt();
        e.lastReconciledAt = o.lastReconciledAt();
        jpa.saveAndFlush(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderRecord> find(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderRecord> findReconcilable() {
        return jpa.findReconcilable().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderRecord> findLiveProtective(UUID positionId) {
        return jpa.findLiveProtective(positionId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderRecord> recent(int limit) {
        return jpa.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream().map(this::toDomain).toList();
    }

    private OrderRecord toDomain(TradingOrderEntity e) {
        return new OrderRecord(e.id, e.proposalId, e.positionId, e.accountId, pairs.symbolOf(e.pairId), OrderRole.valueOf(e.role),
                new ClientOrderId(e.clientOrderId), e.listClientOrderId == null ? null : new ClientOrderId(e.listClientOrderId),
                e.exchangeOrderId, OrderSide.valueOf(e.side), OrderType.valueOf(e.type), e.quantity, e.price, e.stopPrice,
                OrderStatus.valueOf(e.status), e.executedQty, e.cumulativeQuoteQty, e.statusReason, e.fillsRecorded,
                e.createdAt, e.updatedAt, e.lastReconciledAt, e.version);
    }
}
