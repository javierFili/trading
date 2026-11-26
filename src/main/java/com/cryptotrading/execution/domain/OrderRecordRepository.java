package com.cryptotrading.execution.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRecordRepository {

    void save(OrderRecord order);

    Optional<OrderRecord> find(UUID id);

    /** Non-final orders, plus final orders with executions whose fills are not yet recorded. */
    List<OrderRecord> findReconcilable();

    /** Protective orders of a position that are (or may be) live on the exchange. */
    List<OrderRecord> findLiveProtective(UUID positionId);

    List<OrderRecord> recent(int limit);
}
