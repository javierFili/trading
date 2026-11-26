package com.cryptotrading.execution.application;

import com.cryptotrading.configuration.ConditionalOnExchangeMode;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.exchange.domain.ExchangeOrder;
import com.cryptotrading.exchange.domain.Fill;
import com.cryptotrading.exchange.domain.OcoRequest;
import com.cryptotrading.exchange.domain.OrderListCommandResult;
import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.exchange.domain.OrderType;
import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.exchange.domain.ClientOrderId;
import com.cryptotrading.execution.domain.ClientOrderIds;
import com.cryptotrading.execution.domain.FillStore;
import com.cryptotrading.execution.domain.OrderRecord;
import com.cryptotrading.execution.domain.OrderRole;
import com.cryptotrading.market.application.SymbolRulesProvider;
import com.cryptotrading.portfolio.application.PortfolioService;
import com.cryptotrading.portfolio.domain.TradingPosition;
import com.cryptotrading.risk.application.RiskProperties;
import com.cryptotrading.shared.domain.DecimalPolicy;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.trading.domain.ProposalStatus;
import com.cryptotrading.trading.domain.TradeProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles orders that reached a final status: records real fills and commissions, opens/closes positions,
 * and places the protective OCO after an entry. Idempotent through {@code fillsRecorded}.
 */
@Component
@ConditionalOnExchangeMode
public class OrderCompletionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletionHandler.class);

    private final ExecutionSupport support;
    private final ExchangeClient exchange;
    private final FillStore fills;
    private final PortfolioService portfolio;
    private final SymbolRulesProvider rulesProvider;
    private final RiskProperties risk;

    OrderCompletionHandler(ExecutionSupport support, ExchangeClient exchange, FillStore fills, PortfolioService portfolio,
                           SymbolRulesProvider rulesProvider, RiskProperties risk) {
        this.support = support;
        this.exchange = exchange;
        this.fills = fills;
        this.portfolio = portfolio;
        this.rulesProvider = rulesProvider;
        this.risk = risk;
    }

    /**
     * @param quoteFees           commissions charged in the quote asset (or estimated for third assets such as BNB)
     * @param baseCommission      commission charged in the base asset (reduces the quantity actually received)
     * @param baseCommissionValue that base commission valued in the quote asset at the fill price
     */
    record Costs(BigDecimal quoteFees, BigDecimal baseCommission, BigDecimal baseCommissionValue, boolean estimated) {
    }

    public void onFinal(UUID orderId) {
        OrderRecord order = support.orders.find(orderId).orElseThrow();
        if (!order.status().isFinal() || order.fillsRecorded()) {
            return;
        }
        Instant now = support.clock.instant();
        if (!order.hasExecution()) {
            onFinalWithoutExecution(order, now);
            return;
        }
        List<Fill> orderFills = exchange.account().fills(order.symbol(), order.exchangeOrderId());
        Costs costs = costs(order, orderFills);
        UUID[] openedPosition = new UUID[1];
        support.tx.executeWithoutResult(s -> {
            fills.saveAll(order.id(), orderFills);
            BigDecimal quote = order.cumulativeQuoteQuantity();
            switch (order.role()) {
                case ENTRY -> {
                    TradeProposal p = support.proposals.find(order.proposalId()).orElseThrow();
                    BigDecimal netQuantity = order.executedQuantity().subtract(costs.baseCommission());
                    // A base-asset commission is already reflected in the smaller net quantity: not counted twice.
                    TradingPosition position = portfolio.open(order.accountId(), order.symbol(), netQuantity, quote,
                            costs.quoteFees(), p.stopLoss(), p.takeProfit(), ExecutionSupport.ACTOR);
                    openedPosition[0] = position.id();
                    order.markFillsRecorded(position.id(), now);
                }
                case EXIT, TAKE_PROFIT, STOP_LOSS -> {
                    portfolio.close(order.positionId(), order.executedQuantity(), quote,
                            costs.quoteFees().add(costs.baseCommissionValue()),
                            order.role() == OrderRole.EXIT ? "SIGNAL_EXIT" : order.role().name(), ExecutionSupport.ACTOR);
                    order.markFillsRecorded(null, now);
                }
            }
            support.saveOrder(order, costs.estimated() ? "ORDER_FILLED_FEES_ESTIMATED" : "ORDER_FILLED");
        });
        if (order.role() == OrderRole.ENTRY || order.role() == OrderRole.EXIT) {
            boolean complete = order.executedQuantity().compareTo(order.quantity()) >= 0;
            support.updateProposal(order.proposalId(), complete ? "PROPOSAL_FILLED" : "PROPOSAL_PARTIAL_FINAL", p -> {
                if (complete) {
                    p.filled(now);
                } else {
                    p.cancel("Ejecución parcial final: " + order.executedQuantity().toPlainString() + " de "
                            + order.quantity().toPlainString(), now);
                }
            });
        }
        if (openedPosition[0] != null) {
            protect(openedPosition[0], order.proposalId());
        }
    }

    private void onFinalWithoutExecution(OrderRecord order, Instant now) {
        support.tx.executeWithoutResult(s -> {
            order.markFillsRecorded(null, now);
            support.saveOrder(order, "ORDER_CLOSED_WITHOUT_FILL");
        });
        switch (order.role()) {
            case ENTRY -> support.updateProposal(order.proposalId(), "PROPOSAL_FAILED",
                    p -> failIfActive(p, "Orden " + order.status() + " sin ejecución", now));
            case EXIT -> {
                support.updateProposal(order.proposalId(), "PROPOSAL_FAILED",
                        p -> failIfActive(p, "Orden de salida " + order.status() + " sin ejecución", now));
                support.unprotected(order.accountId(), order.positionId(), "la salida no se ejecutó y la OCO estaba cancelada");
            }
            case TAKE_PROFIT, STOP_LOSS -> checkStillProtected(order);
        }
    }

    /** A protective leg ended without execution: fine if the position is closed or the other leg is live. */
    private void checkStillProtected(OrderRecord leg) {
        TradingPosition position = portfolio.find(leg.positionId()).orElse(null);
        if (position == null || position.status() != TradingPosition.Status.OPEN) {
            return;
        }
        if (support.orders.findLiveProtective(position.id()).isEmpty()) {
            portfolio.markProtected(position.id(), false);
            support.unprotected(position.accountId(), position.id(), "las órdenes de protección terminaron sin ejecutarse");
        }
    }

    private static void failIfActive(TradeProposal p, String reason, Instant now) {
        if (p.status() == ProposalStatus.EXECUTING || p.status() == ProposalStatus.PARTIALLY_FILLED) {
            if (p.status() == ProposalStatus.EXECUTING) {
                p.failed(reason, now);
            } else {
                p.cancel(reason, now);
            }
        }
    }

    /** Places the OCO (take profit LIMIT_MAKER + STOP_LOSS_LIMIT) for an open position. */
    public void protect(UUID positionId, UUID proposalId) {
        TradingPosition position = portfolio.find(positionId).orElseThrow();
        if (position.status() != TradingPosition.Status.OPEN || position.protectedByExchange()) {
            return;
        }
        Symbol symbol = position.symbol();
        SymbolRules rules = rulesProvider.rules(symbol);
        BigDecimal free = portfolio.freeBalance(symbol.baseAsset());
        BigDecimal quantity = rules.floorQuantity(position.entryQuantity().min(free), false);
        BigDecimal takeProfit = rules.roundPrice(position.takeProfit(), RoundingMode.DOWN);
        BigDecimal stopTrigger = rules.roundPrice(position.stopLoss(), RoundingMode.UP);
        BigDecimal stopLimit = rules.roundPrice(position.stopLoss().multiply(BigDecimal.ONE.subtract(risk.stopLimitBuffer()),
                DecimalPolicy.CALCULATION), RoundingMode.DOWN);
        List<String> violations = new ArrayList<>(rules.validate(OrderType.LIMIT_MAKER, quantity, takeProfit, takeProfit));
        violations.addAll(rules.validate(OrderType.STOP_LOSS_LIMIT, quantity, stopLimit, stopLimit));
        if (quantity.signum() <= 0 || !violations.isEmpty()) {
            support.unprotected(position.accountId(), positionId, "OCO inválida: " + String.join("; ", violations));
            return;
        }
        ClientOrderId listId = ClientOrderIds.next('L');
        Instant now = support.clock.instant();
        OrderRecord tp = OrderRecord.pending(proposalId, positionId, position.accountId(), symbol, OrderRole.TAKE_PROFIT,
                ClientOrderIds.next(OrderRole.TAKE_PROFIT.code()), listId, OrderSide.SELL, OrderType.LIMIT_MAKER, quantity,
                takeProfit, null, now);
        OrderRecord sl = OrderRecord.pending(proposalId, positionId, position.accountId(), symbol, OrderRole.STOP_LOSS,
                ClientOrderIds.next(OrderRole.STOP_LOSS.code()), listId, OrderSide.SELL, OrderType.STOP_LOSS_LIMIT, quantity,
                stopLimit, stopTrigger, now);
        support.tx.executeWithoutResult(s -> {
            support.saveOrder(tp, "PROTECTION_SUBMITTING");
            support.saveOrder(sl, "PROTECTION_SUBMITTING");
        });
        OrderListCommandResult result = exchange.orders().submitOco(new OcoRequest(listId, tp.clientOrderId(),
                sl.clientOrderId(), symbol, quantity, takeProfit, stopTrigger, stopLimit));
        Instant after = support.clock.instant();
        switch (result) {
            case OrderListCommandResult.Accepted accepted -> support.tx.executeWithoutResult(s -> {
                for (ExchangeOrder o : accepted.orders()) {
                    for (OrderRecord leg : List.of(tp, sl)) {
                        if (o.clientOrderId().equals(leg.clientOrderId())) {
                            leg.apply(o, after);
                            support.saveOrder(leg, "PROTECTION_ACCEPTED");
                        }
                    }
                }
                portfolio.markProtected(positionId, true);
            });
            case OrderListCommandResult.Rejected rejected -> {
                support.tx.executeWithoutResult(s -> {
                    for (OrderRecord leg : List.of(tp, sl)) {
                        leg.markRejected(rejected.errorCode() + " " + rejected.message(), after);
                        leg.markFillsRecorded(null, after);
                        support.saveOrder(leg, "PROTECTION_REJECTED");
                    }
                });
                support.unprotected(position.accountId(), positionId, "OCO rechazada: " + rejected.errorCode() + " " + rejected.message());
            }
            case OrderListCommandResult.Unknown unknown -> {
                support.tx.executeWithoutResult(s -> {
                    for (OrderRecord leg : List.of(tp, sl)) {
                        leg.markUnknown(unknown.reason(), after);
                        support.saveOrder(leg, "PROTECTION_OUTCOME_UNKNOWN");
                    }
                });
                support.breaker.recordFailure(position.accountId(), "Resultado desconocido de la OCO " + listId);
                log.warn("OCO {} outcome unknown; reconciliation will resolve it", listId);
            }
        }
    }

    private Costs costs(OrderRecord order, List<Fill> orderFills) {
        if (orderFills.isEmpty()) {
            BigDecimal estimated = order.cumulativeQuoteQuantity().multiply(risk.feeRate()).setScale(8, RoundingMode.HALF_EVEN);
            return new Costs(estimated, BigDecimal.ZERO, BigDecimal.ZERO, true);
        }
        BigDecimal feesQuote = BigDecimal.ZERO;
        BigDecimal baseCommission = BigDecimal.ZERO;
        BigDecimal baseValue = BigDecimal.ZERO;
        boolean estimated = false;
        for (Fill f : orderFills) {
            if (f.commissionAsset().equals(order.symbol().quoteAsset())) {
                feesQuote = feesQuote.add(f.commission());
            } else if (f.commissionAsset().equals(order.symbol().baseAsset())) {
                baseCommission = baseCommission.add(f.commission());
                baseValue = baseValue.add(f.commission().multiply(f.price()));
            } else {
                // e.g. BNB discount: value unknown without another price; estimate with the configured rate.
                feesQuote = feesQuote.add(f.quoteQuantity().multiply(risk.feeRate()));
                estimated = true;
            }
        }
        return new Costs(feesQuote.setScale(8, RoundingMode.HALF_EVEN), baseCommission,
                baseValue.setScale(8, RoundingMode.HALF_EVEN), estimated);
    }
}
