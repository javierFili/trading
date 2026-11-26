package com.cryptotrading.execution.domain;

import com.cryptotrading.exchange.domain.Fill;

import java.util.List;
import java.util.UUID;

/** Idempotent storage of trades (fills) per order (table {@code trade_execution}). */
public interface FillStore {

    void saveAll(UUID orderId, List<Fill> fills);
}
