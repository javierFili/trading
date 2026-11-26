package com.cryptotrading.trading.domain;

import java.util.UUID;

/** Domain events of the proposal lifecycle. */
public final class ProposalEvents {

    private ProposalEvents() {
    }

    public record Created(UUID proposalId, String symbol, ProposalPurpose purpose) {
    }

    public record StatusChanged(UUID proposalId, ProposalStatus status, String reason) {
    }

    /** Published inside the approval transaction; execution starts only after commit. */
    public record Approved(UUID proposalId, String approvedBy) {
    }
}
