package com.cryptotrading.backtesting.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Persisted backtest run (one dataset segment). */
public record BacktestRun(
        UUID id,
        UUID groupId,
        String strategyCode,
        String strategyVersion,
        Map<String, String> parameters,
        String symbol,
        String timeframe,
        Instant periodStart,
        Instant periodEnd,
        DatasetSplit split,
        BigDecimal feeRate,
        BigDecimal slippageRate,
        BigDecimal initialCapital,
        String status,
        BacktestMetrics metrics,
        String dataChecksum,
        Integer candleCount,
        Integer dataGaps,
        String error,
        String createdBy,
        Instant createdAt,
        Instant finishedAt) {

    public enum DatasetSplit {
        FULL, TRAIN, VALIDATION, TEST
    }
}
