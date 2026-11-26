package com.cryptotrading.backtesting.domain;

import java.util.List;

public record BacktestResult(List<BacktestTrade> trades, BacktestMetrics metrics, int evaluatedCandles) {

    public BacktestResult {
        trades = List.copyOf(trades);
    }
}
