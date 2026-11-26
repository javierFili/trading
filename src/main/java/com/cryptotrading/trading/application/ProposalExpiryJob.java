package com.cryptotrading.trading.application;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.trading.domain.ProposalEvents;
import com.cryptotrading.trading.domain.ProposalRepository;
import com.cryptotrading.trading.domain.ProposalStatus;
import com.cryptotrading.trading.domain.TradeProposal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** Expires PENDING_APPROVAL and APPROVED-but-not-started proposals past their deadline. */
@Component
class ProposalExpiryJob {

    private final ProposalRepository proposals;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    ProposalExpiryJob(ProposalRepository proposals, ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.proposals = proposals;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${trading.expiry-check-interval:30s}")
    void expire() {
        Instant now = clock.instant();
        for (TradeProposal p : proposals.findExpirable(now)) {
            try {
                tx.executeWithoutResult(s -> {
                    TradeProposal fresh = proposals.find(p.id()).orElseThrow();
                    if (fresh.status() != ProposalStatus.PENDING_APPROVAL && fresh.status() != ProposalStatus.APPROVED) {
                        return;
                    }
                    fresh.expire(now);
                    proposals.save(fresh);
                    events.publishEvent(AuditEvent.system("trading", "PROPOSAL_EXPIRED", "proposal", fresh.id(), Map.of()));
                    events.publishEvent(new ProposalEvents.StatusChanged(fresh.id(), ProposalStatus.EXPIRED, fresh.statusReason()));
                });
            } catch (OptimisticLockingFailureException ignored) {
                // changed concurrently (e.g. just approved); next run re-evaluates
            }
        }
    }
}
