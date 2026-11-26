package com.cryptotrading.trading.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.cryptotrading.trading.domain.ProposalStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProposalStatusTest {

    @Test
    void happyPath() {
        ProposalStatus status = PENDING_APPROVAL
                .transitionTo(APPROVED)
                .transitionTo(EXECUTING)
                .transitionTo(PARTIALLY_FILLED)
                .transitionTo(FILLED);
        assertThat(status).isEqualTo(FILLED);
        assertThat(status.isTerminal()).isTrue();
    }

    @Test
    void cannotExecuteWithoutApproval() {
        assertThatThrownBy(() -> PENDING_APPROVAL.transitionTo(EXECUTING))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectedOrExpiredProposalCannotBeRevived() {
        assertThat(REJECTED.canTransitionTo(APPROVED)).isFalse();
        assertThat(EXPIRED.canTransitionTo(APPROVED)).isFalse();
    }

    @Test
    void partiallyFilledCannotFail() {
        // Something was bought: the outcome is a (partial) position, never FAILED.
        assertThat(PARTIALLY_FILLED.canTransitionTo(FAILED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = {"REJECTED", "EXPIRED", "FILLED", "FAILED", "CANCELLED"})
    void terminalStatesHaveNoTransitions(ProposalStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (ProposalStatus target : ProposalStatus.values()) {
            assertThat(terminal.canTransitionTo(target)).isFalse();
        }
    }
}
