package com.cryptotrading.portfolio.domain;

import com.cryptotrading.shared.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository {

    void save(TradingPosition position);

    Optional<TradingPosition> find(UUID id);

    Optional<TradingPosition> findOpen(UUID accountId, Symbol symbol);

    int countOpen(UUID accountId);

    BigDecimal realizedPnlSince(UUID accountId, Instant since);

    List<TradingPosition> recent(UUID accountId, int limit);
}
