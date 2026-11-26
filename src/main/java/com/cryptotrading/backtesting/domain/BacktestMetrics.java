package com.cryptotrading.backtesting.domain;

import com.cryptotrading.shared.domain.DecimalPolicy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Performance metrics. Ratios are {@code null} when undefined (e.g. profit factor without losing trades).
 * Sharpe is computed on per-trade returns (pnl / entry notional), sample standard deviation, NOT annualised.
 * With fewer than {@link #MIN_TRADES_FOR_SIGNIFICANCE} trades the results are not statistically meaningful.
 */
public record BacktestMetrics(
        int trades,
        int wins,
        BigDecimal winRate,
        BigDecimal netProfit,
        BigDecimal returnPct,
        BigDecimal grossProfit,
        BigDecimal grossLoss,
        BigDecimal profitFactor,
        BigDecimal maxDrawdown,
        BigDecimal maxDrawdownPct,
        BigDecimal sharpePerTrade,
        BigDecimal averageTrade,
        BigDecimal averageWin,
        BigDecimal averageLoss,
        BigDecimal realizedRewardToRisk,
        BigDecimal totalFees,
        BigDecimal finalEquity,
        boolean statisticallySignificant) {

    public static final int MIN_TRADES_FOR_SIGNIFICANCE = 30;
    private static final MathContext MC = DecimalPolicy.CALCULATION;

    public static BacktestMetrics compute(List<BacktestTrade> trades, BigDecimal initialCapital,
                                          BigDecimal maxDrawdown, BigDecimal maxDrawdownPct) {
        int n = trades.size();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal grossProfit = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        int wins = 0;
        int losses = 0;
        for (BacktestTrade t : trades) {
            net = net.add(t.pnl());
            fees = fees.add(t.fees());
            if (t.pnl().signum() > 0) {
                wins++;
                grossProfit = grossProfit.add(t.pnl());
            } else if (t.pnl().signum() < 0) {
                losses++;
                grossLoss = grossLoss.add(t.pnl().negate());
            }
        }
        BigDecimal avgWin = wins == 0 ? null : grossProfit.divide(BigDecimal.valueOf(wins), MC);
        BigDecimal avgLoss = losses == 0 ? null : grossLoss.divide(BigDecimal.valueOf(losses), MC);
        return new BacktestMetrics(n, wins,
                n == 0 ? null : s(BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(n), MC)),
                s(net),
                s(net.divide(initialCapital, MC)),
                s(grossProfit), s(grossLoss),
                grossLoss.signum() == 0 ? null : s(grossProfit.divide(grossLoss, MC)),
                s(maxDrawdown), s(maxDrawdownPct),
                sharpe(trades),
                n == 0 ? null : s(net.divide(BigDecimal.valueOf(n), MC)),
                avgWin == null ? null : s(avgWin),
                avgLoss == null ? null : s(avgLoss),
                avgWin == null || avgLoss == null ? null : s(avgWin.divide(avgLoss, MC)),
                s(fees),
                s(initialCapital.add(net)),
                n >= MIN_TRADES_FOR_SIGNIFICANCE);
    }

    static BigDecimal sharpe(List<BacktestTrade> trades) {
        int n = trades.size();
        if (n < 2) {
            return null;
        }
        BigDecimal[] returns = new BigDecimal[n];
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            returns[i] = trades.get(i).pnl().divide(trades.get(i).entryNotional(), MC);
            sum = sum.add(returns[i]);
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), MC);
        BigDecimal sq = BigDecimal.ZERO;
        for (BigDecimal r : returns) {
            BigDecimal d = r.subtract(mean);
            sq = sq.add(d.multiply(d, MC));
        }
        BigDecimal std = sq.divide(BigDecimal.valueOf(n - 1L), MC).sqrt(MC);
        return std.signum() == 0 ? null : s(mean.divide(std, MC));
    }

    private static BigDecimal s(BigDecimal v) {
        return v.setScale(8, RoundingMode.HALF_EVEN);
    }
}
