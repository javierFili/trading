package com.cryptotrading.portfolio.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TradingPositionJpaRepository extends JpaRepository<TradingPositionEntity, UUID> {

    Optional<TradingPositionEntity> findByAccountIdAndPairIdAndStatus(UUID accountId, long pairId, String status);

    int countByAccountIdAndStatus(UUID accountId, String status);

    @Query("SELECT COALESCE(SUM(p.realizedPnl), 0) FROM TradingPositionEntity p WHERE p.accountId = ?1 AND p.status = 'CLOSED' AND p.closedAt >= ?2")
    BigDecimal sumRealizedSince(UUID accountId, Instant since);

    List<TradingPositionEntity> findByAccountIdOrderByOpenedAtDesc(UUID accountId, Pageable pageable);
}
