package com.cryptotrading.execution.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface TradingOrderJpaRepository extends JpaRepository<TradingOrderEntity, UUID> {

    @Query("""
            SELECT o FROM TradingOrderEntity o
            WHERE o.status IN ('PENDING_SUBMIT', 'UNKNOWN', 'NEW', 'PARTIALLY_FILLED')
               OR (o.status IN ('FILLED', 'CANCELED', 'REJECTED', 'EXPIRED') AND o.fillsRecorded = false)
            ORDER BY o.createdAt""")
    List<TradingOrderEntity> findReconcilable();

    @Query("""
            SELECT o FROM TradingOrderEntity o WHERE o.positionId = ?1 AND o.role IN ('TAKE_PROFIT', 'STOP_LOSS')
              AND o.status IN ('PENDING_SUBMIT', 'UNKNOWN', 'NEW', 'PARTIALLY_FILLED')""")
    List<TradingOrderEntity> findLiveProtective(UUID positionId);

    List<TradingOrderEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
