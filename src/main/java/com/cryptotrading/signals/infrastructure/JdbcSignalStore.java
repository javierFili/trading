package com.cryptotrading.signals.infrastructure;

import com.cryptotrading.market.infrastructure.persistence.PairRegistry;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.shared.domain.TradingMode;
import com.cryptotrading.signals.domain.Signal;
import com.cryptotrading.signals.domain.SignalStore;
import com.cryptotrading.strategies.domain.ConfidenceScore;
import com.cryptotrading.strategies.domain.SignalType;
import com.cryptotrading.strategies.domain.StrategyDescriptor;
import com.cryptotrading.strategies.domain.StrategyEvaluation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
class JdbcSignalStore implements SignalStore {

    private static final TypeReference<Map<String, BigDecimal>> DECIMAL_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final String SELECT = """
            SELECT s.id, s.pair_id, s.timeframe, st.code, st.version, s.candle_open_time, s.signal_type,
                   s.reference_price, s.score, s.indicators, s.reasons, s.invalidation, s.suggested_stop,
                   s.suggested_tp, s.created_at
            FROM strategy_signal s JOIN trading_strategy st ON st.id = s.strategy_id
            """;

    private final JdbcTemplate jdbc;
    private final PairRegistry pairs;
    private final JsonMapper json;
    private final Map<String, Long> strategyIds = new ConcurrentHashMap<>();

    JdbcSignalStore(JdbcTemplate jdbc, PairRegistry pairs, JsonMapper json) {
        this.jdbc = jdbc;
        this.pairs = pairs;
        this.json = json;
    }

    @Override
    @Transactional
    public long registerStrategy(StrategyDescriptor d) {
        String key = d.id() + "@" + d.version();
        Long cached = strategyIds.get(key);
        if (cached != null) {
            return cached;
        }
        jdbc.update("""
                INSERT INTO trading_strategy (code, version, timeframe, parameters, description)
                VALUES (?, ?, ?, ?::jsonb, ?) ON CONFLICT (code, version) DO NOTHING""",
                d.id(), d.version(), d.timeframe().name(), json.writeValueAsString(new TreeMap<>(d.parameters())), d.description());
        Long id = jdbc.queryForObject("SELECT id FROM trading_strategy WHERE code = ? AND version = ?", Long.class,
                d.id(), d.version());
        strategyIds.put(key, id);
        return id;
    }

    @Override
    @Transactional
    public boolean save(Signal s, long strategyId, TradingMode mode) {
        StrategyEvaluation e = s.evaluation();
        int rows = jdbc.update("""
                INSERT INTO strategy_signal (id, strategy_id, pair_id, timeframe, candle_open_time, signal_type,
                    reference_price, score, indicators, reasons, invalidation, suggested_stop, suggested_tp,
                    trading_mode, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                ON CONFLICT (strategy_id, pair_id, timeframe, candle_open_time) DO NOTHING""",
                s.id(), strategyId, pairs.idOf(s.symbol()), s.timeframe().name(), Timestamp.from(e.candleOpenTime()),
                e.type().name(), e.referencePrice(), e.score().value(), json.writeValueAsString(e.indicatorValues()),
                json.writeValueAsString(e.reasons()), json.writeValueAsString(e.invalidationConditions()),
                e.suggestedStopLoss(), e.suggestedTakeProfit(), mode.name(), Timestamp.from(s.createdAt()));
        return rows == 1;
    }

    @Override
    @Transactional
    public void saveIndicators(long strategyId, Symbol symbol, Timeframe timeframe, Instant candleOpenTime,
                               Map<String, BigDecimal> values) {
        long pairId = pairs.idOf(symbol);
        values.forEach((name, value) -> jdbc.update("""
                INSERT INTO market_indicator (pair_id, timeframe, candle_open_time, strategy_id, indicator_name, value)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (pair_id, timeframe, candle_open_time, strategy_id, indicator_name) DO NOTHING""",
                pairId, timeframe.name(), Timestamp.from(candleOpenTime), strategyId, name, value));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Signal> find(UUID id) {
        return jdbc.query(SELECT + " WHERE s.id = ?", mapper(), id).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signal> recent(int limit) {
        return jdbc.query(SELECT + " ORDER BY s.created_at DESC LIMIT ?", mapper(), limit);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Map<Instant, BigDecimal>> indicators(Symbol symbol, Timeframe timeframe, Instant from, Instant to) {
        Map<String, Map<Instant, BigDecimal>> result = new TreeMap<>();
        jdbc.query("""
                SELECT indicator_name, candle_open_time, value FROM market_indicator
                WHERE pair_id = ? AND timeframe = ? AND candle_open_time >= ? AND candle_open_time < ?
                ORDER BY candle_open_time""",
                rs -> {
                    result.computeIfAbsent(rs.getString(1), k -> new TreeMap<>())
                            .put(rs.getTimestamp(2).toInstant(), rs.getBigDecimal(3));
                }, pairs.idOf(symbol), timeframe.name(), Timestamp.from(from), Timestamp.from(to));
        return result;
    }

    private RowMapper<Signal> mapper() {
        return (rs, i) -> new Signal(rs.getObject(1, UUID.class), pairs.symbolOf(rs.getLong(2)),
                Timeframe.valueOf(rs.getString(3)), rs.getString(4), rs.getString(5),
                new StrategyEvaluation(SignalType.valueOf(rs.getString(7)), rs.getTimestamp(6).toInstant(),
                        rs.getBigDecimal(8), new ConfidenceScore(rs.getBigDecimal(9)),
                        json.readValue(rs.getString(10), DECIMAL_MAP), json.readValue(rs.getString(11), STRING_LIST),
                        json.readValue(rs.getString(12), STRING_LIST), rs.getBigDecimal(13), rs.getBigDecimal(14)),
                rs.getTimestamp(15).toInstant());
    }
}
