package com.cryptotrading.risk.domain;

import java.util.List;

/** Outcome of a risk evaluation. Every rejection carries all violated rules, not just the first. */
public sealed interface RiskDecision {

    record Approved(PositionSize size) implements RiskDecision {
    }

    record Rejected(List<RiskViolation> violations) implements RiskDecision {
        public Rejected {
            if (violations.isEmpty()) {
                throw new IllegalArgumentException("A rejection needs at least one violation");
            }
            violations = List.copyOf(violations);
        }
    }

    /** @param rule stable code, e.g. {@code DAILY_LOSS_LIMIT}; persisted as a RiskEvent */
    record RiskViolation(String rule, String detail) {
    }
}
