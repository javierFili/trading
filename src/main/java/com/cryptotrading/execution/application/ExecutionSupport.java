package com.cryptotrading.execution.application;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.execution.domain.OrderEvent;
import com.cryptotrading.execution.domain.OrderRecord;
import com.cryptotrading.execution.domain.OrderRecordRepository;
import com.cryptotrading.risk.application.CircuitBreakerService;
import com.cryptotrading.risk.application.RiskEventRecorder;
import com.cryptotrading.risk.domain.RiskAlertEvent;
import com.cryptotrading.trading.domain.ProposalEvents;
import com.cryptotrading.trading.domain.ProposalRepository;
import com.cryptotrading.trading.domain.TradeProposal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Shared helpers: transactional proposal/order updates with audit + events. */
@Component
class ExecutionSupport {

    static final String ACTOR = "system:execution";

    final ProposalRepository proposals;
    final OrderRecordRepository orders;
    final CircuitBreakerService breaker;
    final RiskEventRecorder riskEvents;
    final ApplicationEventPublisher events;
    final TransactionTemplate tx;
    final Clock clock;

    ExecutionSupport(ProposalRepository proposals, OrderRecordRepository orders, CircuitBreakerService breaker,
                     RiskEventRecorder riskEvents, ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.proposals = proposals;
        this.orders = orders;
        this.breaker = breaker;
        this.riskEvents = riskEvents;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /** Re-loads the proposal inside a transaction, applies {@code change} and publishes the status change. */
    void updateProposal(UUID proposalId, String auditAction, Consumer<TradeProposal> change) {
        if (proposalId == null) {
            return;
        }
        tx.executeWithoutResult(s -> {
            TradeProposal p = proposals.find(proposalId).orElseThrow();
            change.accept(p);
            proposals.save(p);
            events.publishEvent(AuditEvent.system("execution", auditAction, "proposal", proposalId,
                    p.statusReason() == null ? Map.of() : Map.of("reason", p.statusReason())));
            events.publishEvent(new ProposalEvents.StatusChanged(proposalId, p.status(), p.statusReason()));
        });
    }

    void cancelProposal(UUID proposalId, String reason) {
        updateProposal(proposalId, "PROPOSAL_CANCELLED", p -> p.cancel(reason, clock.instant()));
        riskEvents.record(null, proposalId, "EXECUTION_CANCELLED", "WARNING", reason);
    }

    void saveOrder(OrderRecord order, String auditAction) {
        orders.save(order);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("clientOrderId", order.clientOrderId().value());
        d.put("role", order.role().name());
        d.put("status", order.status().name());
        d.put("quantity", order.quantity().toPlainString());
        if (order.statusReason() != null) {
            d.put("reason", order.statusReason());
        }
        events.publishEvent(AuditEvent.system("execution", auditAction, "order", order.id(), d));
        events.publishEvent(new OrderEvent(order.id(), order.symbol().toString(), order.role(), order.status(), order.statusReason()));
    }

    /** A position without exchange-side protection is a critical condition: stop everything and alert. */
    void unprotected(UUID accountId, UUID positionId, String reason) {
        String detail = "Posición " + positionId + " SIN protección en el exchange: " + reason + ". Revise y actúe manualmente.";
        breaker.open(accountId, detail, ACTOR);
        events.publishEvent(new RiskAlertEvent(accountId, "POSITION_UNPROTECTED", "CRITICAL", detail));
    }
}
