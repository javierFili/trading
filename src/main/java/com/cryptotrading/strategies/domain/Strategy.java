package com.cryptotrading.strategies.domain;

import com.cryptotrading.market.domain.CandleSeries;

/**
 * A trading strategy. Pure and deterministic: same series, same evaluation. It receives only closed
 * candles, cannot access the exchange, balances or orders, and cannot size positions or override risk
 * limits (enforced by architecture tests). Its output is a recommendation, never an order.
 */
public interface Strategy {

    StrategyDescriptor descriptor();

    /** Minimum number of closed candles required; with fewer the result must be {@link SignalType#NO_ACTION}. */
    int requiredHistory();

    StrategyEvaluation evaluate(CandleSeries closedCandles);
}
