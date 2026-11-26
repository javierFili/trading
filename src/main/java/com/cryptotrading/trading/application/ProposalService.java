package com.cryptotrading.trading.application;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.configuration.ActiveAccount;
import com.cryptotrading.configuration.TradingProperties;
import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.portfolio.application.PortfolioService;
import com.cryptotrading.portfolio.domain.TradingPosition;
import com.cryptotrading.risk.application.RiskEventRecorder;
import com.cryptotrading.risk.application.RiskProperties;
import com.cryptotrading.risk.domain.RiskAlertEvent;
import com.cryptotrading.risk.domain.RiskContext;
import com.cryptotrading.risk.domain.RiskDecision;
import com.cryptotrading.risk.domain.RiskManager;
import com.cryptotrading.risk.domain.TradeIntent;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.signals.domain.Signal;
import com.cryptotrading.signals.domain.SignalCreatedEvent;
import com.cryptotrading.strategies.domain.SignalType;
import com.cryptotrading.strategies.domain.StrategyEvaluation;
import com.cryptotrading.trading.domain.ProposalEvents;
import com.cryptotrading.trading.domain.ProposalPurpose;
import com.cryptotrading.trading.domain.ProposalRepository;
import com.cryptotrading.trading.domain.TradeProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns committed signals into proposals awaiting approval. ENTRY proposals are sized and validated by the
 * {@link RiskManager}; risk rejections are recorded, never silently dropped. Nothing is executed here.
 */
@Service
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    private final ProposalRepository proposals;
    private final RiskManager riskManager;
    private final RiskProperties risk;
    private final RiskContextFactory contexts;
    private final RiskEventRecorder riskEvents;
    private final PortfolioService portfolio;
    private final ActiveAccount account;
    private final TradingProperties trading;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProposalService(ProposalRepository proposals, RiskManager riskManager, RiskProperties risk,
                           RiskContextFactory contexts, RiskEventRecorder riskEvents, PortfolioService portfolio,
                           ActiveAccount account, TradingProperties trading, ApplicationEventPublisher events, Clock clock) {
        this.proposals = proposals;
        this.riskManager = riskManager;
        this.risk = risk;
        this.contexts = contexts;
        this.riskEvents = riskEvents;
        this.portfolio = portfolio;
        this.account = account;
        this.trading = trading;
        this.events = events;
        this.clock = clock;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSignal(SignalCreatedEvent event) {
        if (account.id().isEmpty()) {
            return; // BACKTEST: signals are informative only
        }
        try {
            Signal signal = event.signal();
            if (signal.evaluation().type() == SignalType.ENTRY_LONG) {
                proposeEntry(signal);
            } else if (signal.evaluation().type() == SignalType.EXIT_LONG) {
                proposeExit(signal);
            }
        } catch (RuntimeException e) {
            log.error("Could not build proposal for signal {}: {}", event.signal().id(), e.getMessage(), e);
        }
    }

    void proposeEntry(Signal signal) {
        UUID accountId = account.requireId();
        Symbol symbol = signal.symbol();
        StrategyEvaluation eval = signal.evaluation();
        if (proposals.existsActive(accountId, symbol, ProposalPurpose.ENTRY)) {
            log.info("Entry signal {} ignored: an entry proposal for {} is already active", signal.id(), symbol);
            return;
        }
        TradeIntent intent = new TradeIntent(symbol, eval.referencePrice(), eval.suggestedStopLoss(), eval.suggestedTakeProfit());
        RiskContext context = contexts.build(accountId, symbol);
        RiskDecision decision = riskManager.evaluate(intent, risk.limits(), context);
        if (decision instanceof RiskDecision.Rejected rejected) {
            String detail = rejected.violations().stream().map(v -> v.rule() + ": " + v.detail()).collect(Collectors.joining("; "));
            riskEvents.record(accountId, null, "SIGNAL_REJECTED_BY_RISK", "INFO", "Señal " + signal.id() + " → " + detail);
            events.publishEvent(new RiskAlertEvent(accountId, "SIGNAL_REJECTED_BY_RISK", "INFO", detail));
            events.publishEvent(AuditEvent.system("trading", "SIGNAL_REJECTED_BY_RISK", "signal", signal.id(), Map.of("violations", detail)));
            return;
        }
        var size = ((RiskDecision.Approved) decision).size();
        List<String> justification = new ArrayList<>(eval.reasons());
        justification.add("Puntuación heurística " + eval.score().value().toPlainString() + "/100 (NO es una probabilidad)");
        justification.add("Riesgo si salta el stop: " + size.riskAtStop().toPlainString() + " " + symbol.quoteAsset()
                + " (incluye comisiones, deslizamiento y margen del stop-limit)");
        justification.add("Beneficio/riesgo neto: " + size.rewardToRiskRatio().toPlainString());
        eval.invalidationConditions().forEach(c -> justification.add("Invalidación: " + c));
        TradeProposal proposal = TradeProposal.create(signal.id(), accountId, symbol, null, ProposalPurpose.ENTRY,
                OrderSide.BUY, eval.referencePrice(), eval.suggestedStopLoss(), eval.suggestedTakeProfit(), size.quantity(),
                size.notional(), size.riskAtStop(), size.estimatedFees(), size.rewardToRiskRatio(), justification,
                clock.instant(), trading.approvalTtl(), trading.mode());
        save(proposal);
    }

    void proposeExit(Signal signal) {
        UUID accountId = account.requireId();
        TradingPosition position = portfolio.openPosition(accountId, signal.symbol()).orElse(null);
        if (position == null || proposals.existsActive(accountId, signal.symbol(), ProposalPurpose.EXIT)) {
            return;
        }
        BigDecimal price = signal.evaluation().referencePrice();
        BigDecimal notional = position.entryQuantity().multiply(price).setScale(8, RoundingMode.HALF_EVEN);
        BigDecimal fees = notional.multiply(risk.feeRate()).setScale(8, RoundingMode.HALF_EVEN);
        List<String> justification = new ArrayList<>(signal.evaluation().reasons());
        justification.add("Cierra la posición " + position.id() + " abierta a " + position.averageEntryPrice().toPlainString());
        justification.add("Se cancelará primero la orden OCO de protección");
        TradeProposal proposal = TradeProposal.create(signal.id(), accountId, signal.symbol(), position.id(),
                ProposalPurpose.EXIT, OrderSide.SELL, price, null, null, position.entryQuantity(), notional, null, fees,
                null, justification, clock.instant(), trading.approvalTtl(), trading.mode());
        save(proposal);
    }

    private void save(TradeProposal proposal) {
        proposals.save(proposal);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("symbol", proposal.symbol().toString());
        details.put("purpose", proposal.purpose().name());
        details.put("quantity", proposal.quantity().toPlainString());
        details.put("contentHash", proposal.contentHash());
        events.publishEvent(AuditEvent.system("trading", "PROPOSAL_CREATED", "proposal", proposal.id(), details));
        events.publishEvent(new ProposalEvents.Created(proposal.id(), proposal.symbol().toString(), proposal.purpose()));
        log.info("Proposal {} {} {} qty {} awaiting approval until {}", proposal.id(), proposal.purpose(),
                proposal.symbol(), proposal.quantity(), proposal.expiresAt());
    }
}
