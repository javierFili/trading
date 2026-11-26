package com.cryptotrading.execution.application;

import com.cryptotrading.configuration.ConditionalOnExchangeMode;
import com.cryptotrading.configuration.TradingProperties;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.exchange.domain.ExchangeException;
import com.cryptotrading.exchange.domain.ExchangeOrder;
import com.cryptotrading.exchange.domain.OrderCommandResult;
import com.cryptotrading.exchange.domain.OrderListCommandResult;
import com.cryptotrading.exchange.domain.OrderRequest;
import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.exchange.domain.OrderType;
import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.execution.domain.ClientOrderIds;
import com.cryptotrading.execution.domain.OrderRecord;
import com.cryptotrading.execution.domain.OrderRole;
import com.cryptotrading.execution.domain.OrderStatus;
import com.cryptotrading.market.application.SymbolRulesProvider;
import com.cryptotrading.portfolio.application.PortfolioService;
import com.cryptotrading.portfolio.domain.TradingPosition;
import com.cryptotrading.risk.application.RiskProperties;
import com.cryptotrading.risk.domain.RiskDecision;
import com.cryptotrading.risk.domain.RiskManager;
import com.cryptotrading.risk.domain.TradeIntent;
import com.cryptotrading.shared.domain.BusinessRuleException;
import com.cryptotrading.shared.domain.DecimalPolicy;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.trading.application.RiskContextFactory;
import com.cryptotrading.trading.domain.ProposalPurpose;
import com.cryptotrading.trading.domain.ProposalStatus;
import com.cryptotrading.trading.domain.TradeProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Executes APPROVED proposals. Sequence (ADR-0006):
 * <ol>
 *   <li>Re-check: not expired, breaker closed, price within tolerance, risk re-evaluated with fresh data
 *       (the quantity can only shrink).</li>
 *   <li>Persist the order with its clientOrderId (PENDING_SUBMIT) and move the proposal to EXECUTING.</li>
 *   <li>Send ONCE. Accepted → apply; Rejected → FAILED; Unknown → UNKNOWN, left to reconciliation.</li>
 * </ol>
 * Network calls are never made inside a database transaction.
 */
@Service
@ConditionalOnExchangeMode
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionSupport support;
    private final OrderCompletionHandler completion;
    private final ExchangeClient exchange;
    private final RiskManager riskManager;
    private final RiskProperties risk;
    private final RiskContextFactory contexts;
    private final SymbolRulesProvider rulesProvider;
    private final PortfolioService portfolio;
    private final TradingProperties trading;

    public ExecutionService(ExecutionSupport support, OrderCompletionHandler completion, ExchangeClient exchange,
                            RiskManager riskManager, RiskProperties risk, RiskContextFactory contexts,
                            SymbolRulesProvider rulesProvider, PortfolioService portfolio, TradingProperties trading) {
        this.support = support;
        this.completion = completion;
        this.exchange = exchange;
        this.riskManager = riskManager;
        this.risk = risk;
        this.contexts = contexts;
        this.rulesProvider = rulesProvider;
        this.portfolio = portfolio;
        this.trading = trading;
    }

    public void execute(UUID proposalId) {
        TradeProposal p = support.proposals.find(proposalId).orElse(null);
        if (p == null || p.status() != ProposalStatus.APPROVED) {
            return;
        }
        Instant now = support.clock.instant();
        if (p.isExpired(now)) {
            support.updateProposal(proposalId, "PROPOSAL_EXPIRED", x -> x.expire(now));
            return;
        }
        if (support.breaker.isOpen(p.accountId())) {
            support.cancelProposal(proposalId, "CIRCUIT_BREAKER_OPEN: no se ejecuta ninguna orden");
            return;
        }
        Symbol symbol = p.symbol();
        BigDecimal price;
        SymbolRules rules;
        try {
            price = exchange.marketData().latestPrice(symbol);
            rules = rulesProvider.rules(symbol);
        } catch (ExchangeException e) {
            support.cancelProposal(proposalId, "Datos de mercado no disponibles antes de ejecutar: " + e.getMessage());
            return;
        }
        BigDecimal deviation = price.subtract(p.referencePrice()).abs().divide(p.referencePrice(), DecimalPolicy.CALCULATION);
        if (deviation.compareTo(trading.maxPriceDeviation()) > 0) {
            support.cancelProposal(proposalId, "PRICE_MOVED: " + p.referencePrice().toPlainString() + " → "
                    + price.toPlainString() + " (máx. " + trading.maxPriceDeviation().toPlainString() + "). Se requiere una nueva propuesta.");
            return;
        }
        OrderRequest request = p.purpose() == ProposalPurpose.ENTRY ? prepareEntry(p, price) : prepareExit(p, rules);
        if (request == null) {
            return;
        }
        List<String> violations = rules.validate(OrderType.MARKET, request.quantity(), null, price);
        if (!violations.isEmpty()) {
            support.cancelProposal(proposalId, "Filtros del exchange: " + String.join("; ", violations));
            if (p.purpose() == ProposalPurpose.EXIT) {
                support.unprotected(p.accountId(), p.positionId(), "la OCO fue cancelada y la salida no es válida");
            }
            return;
        }
        OrderRole role = p.purpose() == ProposalPurpose.ENTRY ? OrderRole.ENTRY : OrderRole.EXIT;
        OrderRecord record = OrderRecord.pending(proposalId, p.positionId(), p.accountId(), symbol, role,
                request.clientOrderId(), null, request.side(), OrderType.MARKET, request.quantity(), null, null, now);
        try {
            support.tx.executeWithoutResult(s -> {
                TradeProposal fresh = support.proposals.find(proposalId).orElseThrow();
                fresh.startExecution(support.clock.instant());
                support.proposals.save(fresh);
                support.saveOrder(record, "ORDER_SUBMITTING");
            });
        } catch (BusinessRuleException | OptimisticLockingFailureException e) {
            log.warn("Proposal {} could not start execution: {}", proposalId, e.getMessage());
            return;
        }
        support.updateProposal(proposalId, "PROPOSAL_EXECUTING", x -> { });
        OrderCommandResult result = exchange.orders().submit(request);
        applySubmitResult(record.id(), result);
    }

    private OrderRequest prepareEntry(TradeProposal p, BigDecimal price) {
        RiskDecision decision = riskManager.evaluate(new TradeIntent(p.symbol(), price, p.stopLoss(), p.takeProfit()),
                risk.limits(), contexts.build(p.accountId(), p.symbol()));
        if (decision instanceof RiskDecision.Rejected rejected) {
            support.cancelProposal(p.id(), "RISK_REVALIDATION_FAILED: " + rejected.violations().stream()
                    .map(v -> v.rule() + " " + v.detail()).collect(Collectors.joining("; ")));
            return null;
        }
        BigDecimal quantity = ((RiskDecision.Approved) decision).size().quantity().min(p.quantity());
        return new OrderRequest(ClientOrderIds.next(OrderRole.ENTRY.code()), p.symbol(), OrderSide.BUY, OrderType.MARKET,
                quantity, null, null, null);
    }

    private OrderRequest prepareExit(TradeProposal p, SymbolRules rules) {
        TradingPosition position = portfolio.find(p.positionId()).orElse(null);
        if (position == null || position.status() != TradingPosition.Status.OPEN) {
            support.cancelProposal(p.id(), "POSITION_NOT_OPEN");
            return null;
        }
        List<OrderRecord> protective = support.orders.findLiveProtective(position.id());
        if (!protective.isEmpty()) {
            OrderListCommandResult cancel = exchange.orders().cancelOrderList(p.symbol(), protective.getFirst().listClientOrderId());
            if (!(cancel instanceof OrderListCommandResult.Accepted accepted)) {
                support.cancelProposal(p.id(), "No se pudo cancelar la OCO de protección (" + cancel + "); la protección sigue activa");
                return null;
            }
            boolean protectionFilled = support.tx.execute(s -> {
                boolean filled = false;
                for (OrderRecord leg : protective) {
                    for (ExchangeOrder o : accepted.orders()) {
                        if (o.clientOrderId().equals(leg.clientOrderId())) {
                            leg.apply(o, support.clock.instant());
                            filled |= leg.hasExecution();
                            support.saveOrder(leg, "PROTECTION_CANCELLED");
                        }
                    }
                }
                portfolio.markProtected(position.id(), false);
                return filled;
            });
            if (protectionFilled) {
                support.cancelProposal(p.id(), "La protección ya se había ejecutado; la reconciliación cerrará la posición");
                return null;
            }
        }
        BigDecimal free = portfolio.freeBalance(p.symbol().baseAsset());
        BigDecimal quantity = rules.floorQuantity(position.entryQuantity().min(free), true);
        return new OrderRequest(ClientOrderIds.next(OrderRole.EXIT.code()), p.symbol(), OrderSide.SELL, OrderType.MARKET,
                quantity, null, null, null);
    }

    void applySubmitResult(UUID orderId, OrderCommandResult result) {
        Instant now = support.clock.instant();
        OrderRecord record = support.orders.find(orderId).orElseThrow();
        switch (result) {
            case OrderCommandResult.Accepted accepted -> {
                support.tx.executeWithoutResult(s -> {
                    record.apply(accepted.order(), now);
                    support.saveOrder(record, "ORDER_ACCEPTED");
                });
                support.breaker.recordSuccess(record.accountId());
                if (record.status() == OrderStatus.PARTIALLY_FILLED) {
                    support.updateProposal(record.proposalId(), "PROPOSAL_PARTIALLY_FILLED", x -> x.partiallyFilled(now));
                }
                if (record.status().isFinal()) {
                    completion.onFinal(record.id());
                }
            }
            case OrderCommandResult.Rejected rejected -> {
                String reason = "Rechazada por el exchange: " + rejected.errorCode() + " " + rejected.message();
                support.tx.executeWithoutResult(s -> {
                    record.markRejected(reason, now);
                    support.saveOrder(record, "ORDER_REJECTED");
                });
                support.updateProposal(record.proposalId(), "PROPOSAL_FAILED", x -> x.failed(reason, now));
                support.breaker.recordFailure(record.accountId(), reason);
                if (record.role() == OrderRole.EXIT) {
                    support.unprotected(record.accountId(), record.positionId(), "la orden de salida fue rechazada");
                }
            }
            case OrderCommandResult.Unknown unknown -> {
                support.tx.executeWithoutResult(s -> {
                    record.markUnknown(unknown.reason(), now);
                    support.saveOrder(record, "ORDER_OUTCOME_UNKNOWN");
                });
                support.breaker.recordFailure(record.accountId(), "Resultado desconocido de " + record.clientOrderId() + ": " + unknown.reason());
                log.warn("Order {} outcome unknown ({}); reconciliation will resolve it", record.clientOrderId(), unknown.reason());
            }
        }
    }
}
