package com.cryptotrading.execution.infrastructure;

import com.cryptotrading.configuration.ConditionalOnExchangeMode;
import com.cryptotrading.execution.application.ExecutionService;
import com.cryptotrading.execution.application.ReconciliationService;
import com.cryptotrading.trading.domain.ProposalEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Execution starts only AFTER the approval transaction has committed, asynchronously.
 * Reconciliation runs on a fixed delay (never overlapping itself).
 */
@Component
@ConditionalOnExchangeMode
class ExecutionTriggers {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTriggers.class);

    private final ExecutionService execution;
    private final ReconciliationService reconciliation;

    ExecutionTriggers(ExecutionService execution, ReconciliationService reconciliation) {
        this.execution = execution;
        this.reconciliation = reconciliation;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApproved(ProposalEvents.Approved event) {
        try {
            execution.execute(event.proposalId());
        } catch (RuntimeException e) {
            log.error("Execution of proposal {} failed unexpectedly: {}", event.proposalId(), e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${execution.reconcile-interval:15s}", initialDelayString = "${execution.reconcile-initial-delay:20s}")
    public void reconcile() {
        reconciliation.reconcile();
    }
}
