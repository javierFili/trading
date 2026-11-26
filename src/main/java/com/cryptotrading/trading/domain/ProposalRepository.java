package com.cryptotrading.trading.domain;

import com.cryptotrading.shared.domain.Symbol;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposalRepository {

    /** Optimistic locking: fails if the stored version differs from {@link TradeProposal#version()}. */
    void save(TradeProposal proposal);

    Optional<TradeProposal> find(UUID id);

    List<TradeProposal> findByStatus(Collection<ProposalStatus> statuses, int limit);

    List<TradeProposal> findExpirable(Instant now);

    boolean existsActive(UUID accountId, Symbol symbol, ProposalPurpose purpose);
}
