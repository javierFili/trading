package com.cryptotrading.backtesting.infrastructure;

import com.cryptotrading.backtesting.domain.BacktestMetrics;
import com.cryptotrading.backtesting.domain.BacktestRun;
import com.cryptotrading.backtesting.domain.BacktestStore;
import com.cryptotrading.backtesting.domain.BacktestTrade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcBacktestStore implements BacktestStore {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final String SELECT = """
            SELECT id, group_id, strategy_code, strategy_version, parameters::text, symbol_code, timeframe, period_start,
                   period_end, dataset_split, fee_rate, slippage_rate, initial_capital, status, metrics::text,
                   data_checksum, candle_count, error, created_by, created_at, finished_at
            FROM backtest_run""";

    record StoredMetrics(BacktestMetrics metrics, int dataGaps) {
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    JdbcBacktestStore(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void create(BacktestRun r, Map<String, Object> riskLimits) {
        jdbc.update("""
                INSERT INTO backtest_run (id, group_id, strategy_code, strategy_version, parameters, symbol_code, timeframe,
                    period_start, period_end, dataset_split, fee_rate, slippage_rate, initial_capital, risk_limits,
                    status, created_by, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)""",
                r.id(), r.groupId(), r.strategyCode(), r.strategyVersion(), json.writeValueAsString(r.parameters()),
                r.symbol(), r.timeframe(), Timestamp.from(r.periodStart()), Timestamp.from(r.periodEnd()),
                r.split().name(), r.feeRate(), r.slippageRate(), r.initialCapital(), json.writeValueAsString(riskLimits),
                r.status(), r.createdBy(), Timestamp.from(r.createdAt()));
    }

    @Override
    @Transactional
    public void complete(UUID runId, BacktestMetrics metrics, int dataGaps, List<BacktestTrade> trades, String checksum,
                         int candleCount) {
        jdbc.update("UPDATE backtest_run SET status = 'COMPLETED', metrics = ?::jsonb, data_checksum = ?, candle_count = ?, "
                        + "finished_at = ? WHERE id = ?",
                json.writeValueAsString(new StoredMetrics(metrics, dataGaps)), checksum, candleCount,
                Timestamp.from(clock.instant()), runId);
        jdbc.batchUpdate("""
                INSERT INTO backtest_trade (run_id, entry_time, entry_price, exit_time, exit_price, quantity, fees, pnl, exit_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", trades, 500, (ps, t) -> {
            ps.setObject(1, runId);
            ps.setTimestamp(2, Timestamp.from(t.entryTime()));
            ps.setBigDecimal(3, t.entryPrice());
            ps.setTimestamp(4, Timestamp.from(t.exitTime()));
            ps.setBigDecimal(5, t.exitPrice());
            ps.setBigDecimal(6, t.quantity());
            ps.setBigDecimal(7, t.fees());
            ps.setBigDecimal(8, t.pnl());
            ps.setString(9, t.exitReason().name());
        });
    }

    @Override
    @Transactional
    public void fail(UUID runId, String error) {
        String message = error == null ? "unknown error" : error.length() > 1000 ? error.substring(0, 1000) : error;
        jdbc.update("UPDATE backtest_run SET status = 'FAILED', error = ?, finished_at = ? WHERE id = ?",
                message, Timestamp.from(clock.instant()), runId);
    }

    @Override
    public Optional<BacktestRun> find(UUID runId) {
        return jdbc.query(SELECT + " WHERE id = ?", mapper(), runId).stream().findFirst();
    }

    @Override
    public List<BacktestRun> group(UUID groupId) {
        return jdbc.query(SELECT + " WHERE group_id = ? ORDER BY period_start", mapper(), groupId);
    }

    @Override
    public List<BacktestRun> recent(int limit) {
        return jdbc.query(SELECT + " ORDER BY created_at DESC, period_start LIMIT ?", mapper(), limit);
    }

    @Override
    public List<BacktestTrade> trades(UUID runId) {
        return jdbc.query("SELECT entry_time, entry_price, exit_time, exit_price, quantity, fees, pnl, exit_reason "
                        + "FROM backtest_trade WHERE run_id = ? ORDER BY entry_time",
                (rs, i) -> new BacktestTrade(rs.getTimestamp(1).toInstant(), rs.getBigDecimal(2),
                        rs.getTimestamp(3).toInstant(), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6),
                        rs.getBigDecimal(7), BacktestTrade.ExitReason.valueOf(rs.getString(8))), runId);
    }

    private RowMapper<BacktestRun> mapper() {
        return (rs, i) -> {
            String metricsJson = rs.getString(15);
            StoredMetrics stored = metricsJson == null ? null : json.readValue(metricsJson, StoredMetrics.class);
            Timestamp finished = rs.getTimestamp(21);
            Integer count = rs.getObject(17, Integer.class);
            return new BacktestRun(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                    rs.getString(4), json.readValue(rs.getString(5), STRING_MAP), rs.getString(6), rs.getString(7),
                    rs.getTimestamp(8).toInstant(), rs.getTimestamp(9).toInstant(),
                    BacktestRun.DatasetSplit.valueOf(rs.getString(10)), rs.getBigDecimal(11), rs.getBigDecimal(12),
                    rs.getBigDecimal(13), rs.getString(14), stored == null ? null : stored.metrics(), rs.getString(16),
                    count, stored == null ? null : stored.dataGaps(), rs.getString(18),
                    rs.getString(19), rs.getTimestamp(20).toInstant(), finished == null ? null : finished.toInstant());
        };
    }
}
