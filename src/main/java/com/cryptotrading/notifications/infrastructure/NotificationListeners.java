package com.cryptotrading.notifications.infrastructure;

import com.cryptotrading.execution.domain.OrderEvent;
import com.cryptotrading.market.domain.CandleClosedEvent;
import com.cryptotrading.portfolio.domain.PositionEvent;
import com.cryptotrading.risk.domain.RiskAlertEvent;
import com.cryptotrading.signals.domain.SignalCreatedEvent;
import com.cryptotrading.trading.domain.ProposalEvents;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** Forwards domain events to clients only after their transaction committed (no phantom notifications). */
@Component
class NotificationListeners {

    private final NotificationHub hub;

    NotificationListeners(NotificationHub hub) {
        this.hub = hub;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void proposalCreated(ProposalEvents.Created e) {
        hub.broadcast("PROPOSAL_CREATED", e);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void proposalStatus(ProposalEvents.StatusChanged e) {
        hub.broadcast("PROPOSAL_STATUS", e);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void riskAlert(RiskAlertEvent e) {
        hub.broadcast("RISK_ALERT", e);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void position(PositionEvent e) {
        hub.broadcast("POSITION", e);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void order(OrderEvent e) {
        hub.broadcast("ORDER", e);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void signal(SignalCreatedEvent e) {
        var s = e.signal();
        hub.broadcast("SIGNAL", Map.of("id", s.id(), "symbol", s.symbol().toString(), "type", s.evaluation().type(),
                "score", s.evaluation().score().value(), "candleOpenTime", s.evaluation().candleOpenTime()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void candle(CandleClosedEvent e) {
        hub.broadcast("CANDLE_CLOSED", Map.of("symbol", e.symbol().toString(), "timeframe", e.timeframe(),
                "candleOpenTime", e.candleOpenTime()));
    }
}
