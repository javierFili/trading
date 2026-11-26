package com.cryptotrading.backtesting.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BacktestStore {

    void create(BacktestRun run, Map<String, Object> riskLimits);

    void complete(UUID runId, BacktestMetrics metrics, int dataGaps, List<BacktestTrade> trades, String checksum,
                  int candleCount);

    void fail(UUID runId, String error);

    Optional<BacktestRun> find(UUID runId);

    List<BacktestRun> group(UUID groupId);

    List<BacktestRun> recent(int limit);

    List<BacktestTrade> trades(UUID runId);
}
