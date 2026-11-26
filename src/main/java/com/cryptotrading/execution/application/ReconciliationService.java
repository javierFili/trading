package com.cryptotrading.execution.application;

import com.cryptotrading.configuration.ConditionalOnExchangeMode;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.exchange.domain.ExchangeException;
import com.cryptotrading.exchange.domain.ExchangeOrder;
import com.cryptotrading.execution.domain.OrderRecord;
import com.cryptotrading.execution.domain.OrderRole;
import com.cryptotrading.execution.domain.OrderStatus;
import com.cryptotrading.trading.domain.ProposalStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Converges local order state with the exchange. Resolves UNKNOWN / PENDING_SUBMIT orders by looking them
 * up with their clientOrderId, tracks open orders until final, and finishes orders whose fills are pending.
 * An order is declared non-existent only after the exchange explicitly says so AND a grace period passed.
 */
@Service
@ConditionalOnExchangeMode
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    static final Duration IN_FLIGHT_GRACE = Duration.ofSeconds(30);
    static final Duration NOT_FOUND_GRACE = Duration.ofMinutes(2);

    private final ExecutionSupport support;
    private final OrderCompletionHandler completion;
    private final ExchangeClient exchange;

    public ReconciliationService(ExecutionSupport support, OrderCompletionHandler completion, ExchangeClient exchange) {
        this.support = support;
        this.completion = completion;
        this.exchange = exchange;
    }

    public int reconcile() {
        int changed = 0;
        for (OrderRecord order : support.orders.findReconcilable()) {
            try {
                changed += reconcile(order) ? 1 : 0;
            } catch (ExchangeException e) {
                log.warn("Reconciliation of {} postponed: {}", order.clientOrderId(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("Reconciliation of {} failed: {}", order.clientOrderId(), e.getMessage(), e);
            }
        }
        return changed;
    }

    private boolean reconcile(OrderRecord order) {
        Instant now = support.clock.instant();
        if (order.status().isFinal()) {
            completion.onFinal(order.id());
            return true;
        }
        if (order.status() == OrderStatus.PENDING_SUBMIT && order.createdAt().plus(IN_FLIGHT_GRACE).isAfter(now)) {
            return false; // may still be in flight in the executing thread
        }
        Optional<ExchangeOrder> found = exchange.account().findOrder(order.symbol(), order.clientOrderId());
        if (found.isPresent()) {
            OrderStatus before = order.status();
            support.tx.executeWithoutResult(s -> {
                order.apply(found.get(), now);
                support.saveOrder(order, before == order.status() ? "ORDER_RECONCILED" : "ORDER_STATUS_CHANGED");
            });
            if (before == OrderStatus.UNKNOWN || before == OrderStatus.PENDING_SUBMIT) {
                support.breaker.recordSuccess(order.accountId());
            }
            if (order.status() == OrderStatus.PARTIALLY_FILLED && (order.role() == OrderRole.ENTRY || order.role() == OrderRole.EXIT)) {
                support.updateProposal(order.proposalId(), "PROPOSAL_PARTIALLY_FILLED", p -> {
                    if (p.status() == ProposalStatus.EXECUTING) {
                        p.partiallyFilled(now);
                    }
                });
            }
            if (order.status().isFinal()) {
                completion.onFinal(order.id());
            }
            return before != order.status();
        }
        if (order.createdAt().plus(NOT_FOUND_GRACE).isAfter(now)) {
            return false;
        }
        String reason = order.status() == OrderStatus.NEW || order.status() == OrderStatus.PARTIALLY_FILLED
                ? "El exchange ya no reconoce una orden que estaba activa (¿reinicio del testnet?)"
                : "El exchange confirma que la orden no existe: nunca se ejecutó";
        support.tx.executeWithoutResult(s -> {
            if (order.status() == OrderStatus.NEW || order.status() == OrderStatus.PARTIALLY_FILLED) {
                order.markExpired(reason, now);
            } else {
                order.markRejected(reason, now);
            }
            support.saveOrder(order, "ORDER_NOT_FOUND");
        });
        support.riskEvents.record(order.accountId(), order.proposalId(), "ORDER_NOT_FOUND", "WARNING",
                order.clientOrderId() + ": " + reason);
        completion.onFinal(order.id());
        return true;
    }
}
