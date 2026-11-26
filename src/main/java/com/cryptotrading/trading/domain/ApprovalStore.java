package com.cryptotrading.trading.domain;

import java.time.Instant;
import java.util.UUID;

/** Persists the single, immutable approval/rejection decision of a proposal (table {@code trade_approval}). */
public interface ApprovalStore {

    void record(UUID proposalId, long proposalVersion, String contentHash, String decision, String decidedBy,
                Instant decidedAt, String clientIp);
}
