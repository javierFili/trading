package com.cryptotrading.trading.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface TradeProposalJpaRepository extends JpaRepository<TradeProposalEntity, UUID> {

    List<TradeProposalEntity> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses, Pageable pageable);

    List<TradeProposalEntity> findByStatusInAndExpiresAtLessThanEqual(Collection<String> statuses, Instant now);

    boolean existsByAccountIdAndPairIdAndPurposeAndStatusIn(UUID accountId, long pairId, String purpose, Collection<String> statuses);
}
