package com.cryptotrading.risk.domain;

import com.cryptotrading.exchange.domain.OrderType;
import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.shared.domain.DecimalPolicy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Conservative risk manager for long spot entries executed as MARKET orders and protected by a
 * stop-loss-limit order. All violations are collected; sizing only ever shrinks a position.
 *
 * <pre>
 * e            = entry × (1 + slippage)                        expected fill
 * x            = stop × (1 − stopLimitBuffer)                  worst stop fill
 * lossPerUnit  = (e − x) + e·fee + x·fee
 * riskBudget   = min(maxRiskPerTrade, maxDailyLoss − lossToday)
 * quantity     = floor_step( min(riskBudget / lossPerUnit,
 *                                maxPositionNotional / e,
 *                                (free − reserve) / (e·(1 + fee))) )
 * reward/risk  = (tp − e − e·fee − tp·fee) / lossPerUnit
 * </pre>
 */
public final class DefaultRiskManager implements RiskManager {

    private static final MathContext MC = DecimalPolicy.CALCULATION;

    @Override
    public RiskDecision evaluate(TradeIntent intent, RiskLimits limits, RiskContext ctx) {
        List<RiskDecision.RiskViolation> violations = new ArrayList<>();
        SymbolRules rules = ctx.symbolRules();

        if (ctx.circuitBreakerOpen()) {
            violations.add(v("CIRCUIT_BREAKER_OPEN", "El circuit breaker está abierto"));
        }
        if (ctx.openPositions() >= limits.maxOpenPositions()) {
            violations.add(v("MAX_OPEN_POSITIONS", ctx.openPositions() + " posiciones abiertas (máx. " + limits.maxOpenPositions() + ")"));
        }
        if (!rules.symbol().equals(intent.symbol())) {
            violations.add(v("SYMBOL_RULES_MISMATCH", "Reglas de " + rules.symbol() + " para " + intent.symbol()));
        }
        if (rules.isStale(ctx.evaluatedAt(), ctx.maxRulesAge())) {
            violations.add(v("SYMBOL_RULES_STALE", "Filtros del exchange obtenidos en " + rules.fetchedAt()));
        }

        BigDecimal fee = ctx.feeRate();
        BigDecimal one = BigDecimal.ONE;
        BigDecimal e = intent.entryPrice().multiply(one.add(ctx.slippageRate()), MC);
        BigDecimal x = intent.stopLoss().multiply(one.subtract(ctx.stopLimitBuffer()), MC);
        BigDecimal lossPerUnit = e.subtract(x).add(e.multiply(fee, MC)).add(x.multiply(fee, MC));
        BigDecimal rewardPerUnit = intent.takeProfit().subtract(e).subtract(e.multiply(fee, MC))
                .subtract(intent.takeProfit().multiply(fee, MC));

        BigDecimal dailyRoom = limits.maxDailyLoss().subtract(ctx.lossToday());
        BigDecimal riskBudget = limits.maxRiskPerTrade().min(dailyRoom);
        if (dailyRoom.signum() <= 0) {
            violations.add(v("DAILY_LOSS_LIMIT", "Pérdida del día " + ctx.lossToday().toPlainString()
                    + " ≥ límite " + limits.maxDailyLoss().toPlainString()));
        }
        BigDecimal spendable = ctx.freeQuoteBalance().subtract(limits.minQuoteReserve());
        if (spendable.signum() <= 0) {
            violations.add(v("MIN_RESERVE", "Saldo libre " + ctx.freeQuoteBalance().toPlainString()
                    + " no supera la reserva " + limits.minQuoteReserve().toPlainString()));
        }

        BigDecimal rewardToRisk = rewardPerUnit.divide(lossPerUnit, MC);
        if (rewardToRisk.compareTo(limits.minRewardToRisk()) < 0) {
            violations.add(v("MIN_REWARD_TO_RISK", "Beneficio/riesgo neto " + rewardToRisk.setScale(2, RoundingMode.HALF_EVEN)
                    + " < " + limits.minRewardToRisk().toPlainString()));
        }

        BigDecimal quantity = BigDecimal.ZERO;
        if (riskBudget.signum() > 0 && spendable.signum() > 0) {
            BigDecimal byRisk = riskBudget.divide(lossPerUnit, MC);
            BigDecimal byNotional = limits.maxPositionNotional().divide(e, MC);
            BigDecimal byBalance = spendable.divide(e.multiply(one.add(fee), MC), MC);
            quantity = rules.floorQuantity(byRisk.min(byNotional).min(byBalance), true);
            if (quantity.signum() <= 0) {
                violations.add(v("POSITION_TOO_SMALL", "El riesgo permitido no alcanza ni un incremento de cantidad"));
            } else {
                for (String filter : rules.validate(OrderType.MARKET, quantity, null, e)) {
                    violations.add(v("EXCHANGE_FILTER", filter));
                }
            }
        }

        if (!violations.isEmpty()) {
            return new RiskDecision.Rejected(violations);
        }
        BigDecimal notional = quantity.multiply(e, MC);
        BigDecimal risk = quantity.multiply(lossPerUnit, MC);
        BigDecimal fees = quantity.multiply(e.multiply(fee, MC).add(x.multiply(fee, MC)), MC);
        return new RiskDecision.Approved(new PositionSize(quantity, scale(e), scale(notional), scale(risk),
                scale(fees), rewardToRisk.setScale(4, RoundingMode.HALF_EVEN)));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_EVEN);
    }

    private static RiskDecision.RiskViolation v(String rule, String detail) {
        return new RiskDecision.RiskViolation(rule, detail);
    }
}
