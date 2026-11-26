package com.cryptotrading.backtesting.domain;

import com.cryptotrading.market.domain.CandleSeries;
import com.cryptotrading.risk.domain.RiskContext;
import com.cryptotrading.risk.domain.RiskDecision;
import com.cryptotrading.risk.domain.TradeIntent;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.DecimalPolicy;
import com.cryptotrading.strategies.domain.SignalType;
import com.cryptotrading.strategies.domain.StrategyEvaluation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event-driven, candle-by-candle simulator. Independent of any exchange.
 *
 * <ul>
 *   <li>The strategy is evaluated at the close of candle t with a {@link CandleSeries} whose {@code asOf}
 *       is that close: it can never see candle t+1 (look-ahead protection).</li>
 *   <li>Entries/exits decided at t are filled at the OPEN of t+1, plus/minus slippage.</li>
 *   <li>Positions are sized by the real {@link com.cryptotrading.risk.domain.RiskManager} with the same limits.</li>
 *   <li>Stop and take profit are checked intrabar; if both are touched in the same candle the STOP is assumed
 *       first (conservative). A gap through the stop fills at the open.</li>
 *   <li>Fees are charged on both sides. Open positions are closed at the last close (reason END).</li>
 * </ul>
 */
public final class BacktestEngine {

    private static final MathContext MC = DecimalPolicy.CALCULATION;

    private record Position(Instant entryTime, BigDecimal entryPrice, BigDecimal quantity, BigDecimal entryCost,
                            BigDecimal entryFee, BigDecimal stop, BigDecimal takeProfit) {
    }

    private record PendingEntry(BigDecimal quantity, BigDecimal stop, BigDecimal takeProfit) {
    }

    public BacktestResult run(List<Candle> candles, BacktestConfig cfg) {
        int lookback = cfg.strategy().requiredHistory();
        BigDecimal fee = cfg.feeRate();
        BigDecimal slip = cfg.slippageRate();
        BigDecimal quote = cfg.initialCapital();
        Position position = null;
        PendingEntry pendingEntry = null;
        boolean pendingExit = false;
        List<BacktestTrade> trades = new ArrayList<>();
        Map<LocalDate, BigDecimal> pnlByDay = new HashMap<>();
        BigDecimal peak = quote;
        BigDecimal maxDd = BigDecimal.ZERO;
        BigDecimal maxDdPct = BigDecimal.ZERO;
        int evaluated = 0;
        Candle lastTraded = null;

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (c.openTime().isBefore(cfg.tradingStart())) {
                continue;
            }
            if (!c.openTime().isBefore(cfg.tradingEnd())) {
                break;
            }
            lastTraded = c;

            // 1. Orders decided at the previous close are filled at this open.
            if (pendingExit && position != null) {
                BigDecimal fill = c.open().multiply(BigDecimal.ONE.subtract(slip), MC);
                quote = close(position, fill, c.openTime(), BacktestTrade.ExitReason.SIGNAL, fee, trades, pnlByDay, quote);
                position = null;
            }
            if (pendingEntry != null && position == null) {
                BigDecimal fill = c.open().multiply(BigDecimal.ONE.add(slip), MC);
                BigDecimal cost = pendingEntry.quantity().multiply(fill, MC);
                BigDecimal entryFee = cost.multiply(fee, MC);
                boolean gappedThroughStop = fill.compareTo(pendingEntry.stop()) <= 0;
                boolean affordable = cost.add(entryFee).compareTo(quote.subtract(cfg.limits().minQuoteReserve())) <= 0;
                if (!gappedThroughStop && affordable) {
                    quote = quote.subtract(cost).subtract(entryFee);
                    position = new Position(c.openTime(), fill, pendingEntry.quantity(), cost, entryFee,
                            pendingEntry.stop(), pendingEntry.takeProfit());
                }
            }
            pendingEntry = null;
            pendingExit = false;

            // 2. Intrabar protective exits (stop first).
            if (position != null) {
                if (c.low().compareTo(position.stop()) <= 0) {
                    BigDecimal trigger = c.open().compareTo(position.stop()) < 0 ? c.open() : position.stop();
                    BigDecimal fill = trigger.multiply(BigDecimal.ONE.subtract(slip), MC);
                    quote = close(position, fill, c.openTime(), BacktestTrade.ExitReason.STOP_LOSS, fee, trades, pnlByDay, quote);
                    position = null;
                } else if (c.high().compareTo(position.takeProfit()) >= 0) {
                    BigDecimal fill = c.open().max(position.takeProfit());
                    quote = close(position, fill, c.openTime(), BacktestTrade.ExitReason.TAKE_PROFIT, fee, trades, pnlByDay, quote);
                    position = null;
                }
            }

            // 3. Strategy decision at this close, with data up to and including this candle only.
            if (i + 1 >= lookback) {
                Instant asOf = c.closeTime().plusMillis(1);
                CandleSeries window = CandleSeries.of(c.symbol(), c.timeframe(), asOf, candles.subList(i + 1 - lookback, i + 1));
                StrategyEvaluation eval = cfg.strategy().evaluate(window);
                evaluated++;
                if (eval.type() == SignalType.ENTRY_LONG && position == null && eval.suggestedStopLoss() != null) {
                    pendingEntry = size(eval, cfg, quote, asOf, pnlByDay);
                } else if (eval.type() == SignalType.EXIT_LONG && position != null) {
                    pendingExit = true;
                }
            }

            // 4. Mark to market.
            BigDecimal equity = position == null ? quote : quote.add(position.quantity().multiply(c.close(), MC));
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            BigDecimal dd = peak.subtract(equity);
            if (dd.compareTo(maxDd) > 0) {
                maxDd = dd;
                maxDdPct = dd.divide(peak, MC);
            }
        }
        if (position != null && lastTraded != null) {
            BigDecimal fill = lastTraded.close().multiply(BigDecimal.ONE.subtract(slip), MC);
            close(position, fill, lastTraded.closeTime(), BacktestTrade.ExitReason.END, fee, trades, pnlByDay, quote);
        }
        return new BacktestResult(trades, BacktestMetrics.compute(trades, cfg.initialCapital(), maxDd, maxDdPct), evaluated);
    }

    private PendingEntry size(StrategyEvaluation eval, BacktestConfig cfg, BigDecimal quote, Instant asOf,
                              Map<LocalDate, BigDecimal> pnlByDay) {
        TradeIntent intent;
        try {
            intent = new TradeIntent(cfg.rules().symbol(), eval.referencePrice(), eval.suggestedStopLoss(), eval.suggestedTakeProfit());
        } catch (IllegalArgumentException e) {
            return null;
        }
        BigDecimal dayPnl = pnlByDay.getOrDefault(LocalDate.ofInstant(asOf, ZoneOffset.UTC), BigDecimal.ZERO);
        BigDecimal lossToday = dayPnl.signum() < 0 ? dayPnl.negate() : BigDecimal.ZERO;
        RiskContext ctx = new RiskContext(asOf, quote, 0, lossToday, false, cfg.rules().withFetchedAt(asOf),
                cfg.feeRate(), cfg.slippageRate(), cfg.stopLimitBuffer(), Duration.ofDays(1));
        RiskDecision decision = cfg.riskManager().evaluate(intent, cfg.limits(), ctx);
        if (decision instanceof RiskDecision.Approved approved) {
            return new PendingEntry(approved.size().quantity(), intent.stopLoss(), intent.takeProfit());
        }
        return null;
    }

    private static BigDecimal close(Position p, BigDecimal fill, Instant at, BacktestTrade.ExitReason reason, BigDecimal fee,
                                    List<BacktestTrade> trades, Map<LocalDate, BigDecimal> pnlByDay, BigDecimal quote) {
        BigDecimal proceeds = p.quantity().multiply(fill, MC);
        BigDecimal exitFee = proceeds.multiply(fee, MC);
        BigDecimal pnl = proceeds.subtract(exitFee).subtract(p.entryCost()).subtract(p.entryFee());
        trades.add(new BacktestTrade(p.entryTime(), p.entryPrice(), at, fill, p.quantity(), p.entryFee().add(exitFee), pnl, reason));
        pnlByDay.merge(LocalDate.ofInstant(at, ZoneOffset.UTC), pnl, BigDecimal::add);
        return quote.add(proceeds).subtract(exitFee);
    }
}
