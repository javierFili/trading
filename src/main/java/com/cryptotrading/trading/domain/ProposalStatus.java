package com.cryptotrading.trading.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a trade proposal. Transitions are explicit; anything not listed here is illegal.
 *
 * <pre>
 * PENDING_APPROVAL -> APPROVED | REJECTED | EXPIRED | CANCELLED
 * APPROVED         -> EXECUTING | EXPIRED | CANCELLED   (CANCELLED: pre-execution re-check failed)
 * EXECUTING        -> PARTIALLY_FILLED | FILLED | FAILED | CANCELLED
 * PARTIALLY_FILLED -> PARTIALLY_FILLED | FILLED | CANCELLED
 * </pre>
 *
 * EXECUTING with an unknown exchange outcome stays EXECUTING until reconciliation; FAILED is only used
 * once it is confirmed that no order exists on the exchange.
 */
public enum ProposalStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    EXPIRED,
    EXECUTING,
    PARTIALLY_FILLED,
    FILLED,
    FAILED,
    CANCELLED;

    private static final Map<ProposalStatus, Set<ProposalStatus>> TRANSITIONS = Map.of(
            PENDING_APPROVAL, EnumSet.of(APPROVED, REJECTED, EXPIRED, CANCELLED),
            APPROVED, EnumSet.of(EXECUTING, EXPIRED, CANCELLED),
            EXECUTING, EnumSet.of(PARTIALLY_FILLED, FILLED, FAILED, CANCELLED),
            PARTIALLY_FILLED, EnumSet.of(PARTIALLY_FILLED, FILLED, CANCELLED));

    public boolean canTransitionTo(ProposalStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return !TRANSITIONS.containsKey(this);
    }

    public ProposalStatus transitionTo(ProposalStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Illegal proposal transition " + this + " -> " + target);
        }
        return target;
    }
}
