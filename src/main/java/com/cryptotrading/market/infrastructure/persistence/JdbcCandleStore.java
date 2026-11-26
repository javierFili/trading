package com.cryptotrading.market.infrastructure.persistence;

import com.cryptotrading.market.domain.CandleStore;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** JDBC adapter: bulk, idempotent inserts ({@code ON CONFLICT DO NOTHING}) are the main workload. */
@Repository
class JdbcCandleStore implements CandleStore {

    private static final String INSERT = """
            INSERT INTO market_candle (pair_id, timeframe, open_time, close_time, open, high, low, close, volume,
                                       quote_volume, trade_count, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (pair_id, timeframe, open_time) DO NOTHING
            """;
    private static final String COLUMNS = "open_time, close_time, open, high, low, close, volume, quote_volume, trade_count";

    private final JdbcTemplate jdbc;
    private final PairRegistry pairs;

    JdbcCandleStore(JdbcTemplate jdbc, PairRegistry pairs) {
        this.jdbc = jdbc;
        this.pairs = pairs;
    }

    @Override
    @Transactional
    public int saveAll(List<Candle> candles, String source) {
        if (candles.isEmpty()) {
            return 0;
        }
        long pairId = pairs.idOf(candles.getFirst().symbol());
        List<Object[]> rows = new ArrayList<>(candles.size());
        for (Candle c : candles) {
            rows.add(new Object[]{pairId, c.timeframe().name(), Timestamp.from(c.openTime()), Timestamp.from(c.closeTime()),
                    c.open(), c.high(), c.low(), c.close(), c.volume(), c.quoteVolume(), c.tradeCount(), source});
        }
        int inserted = 0;
        for (int[] batch : jdbc.batchUpdate(INSERT, rows, 500, (ps, row) -> {
            for (int i = 0; i < row.length; i++) {
                ps.setObject(i + 1, row[i]);
            }
        })) {
            for (int count : batch) {
                inserted += Math.max(count, 0);
            }
        }
        return inserted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candle> findRange(Symbol symbol, Timeframe timeframe, Instant fromInclusive, Instant toExclusive) {
        return jdbc.query("SELECT " + COLUMNS + " FROM market_candle WHERE pair_id = ? AND timeframe = ? "
                        + "AND open_time >= ? AND open_time < ? ORDER BY open_time",
                mapper(symbol, timeframe), pairs.idOf(symbol), timeframe.name(),
                Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candle> findLatestClosed(Symbol symbol, Timeframe timeframe, Instant asOf, int count) {
        List<Candle> desc = new ArrayList<>(jdbc.query("SELECT " + COLUMNS + " FROM market_candle WHERE pair_id = ? "
                        + "AND timeframe = ? AND close_time < ? ORDER BY open_time DESC LIMIT ?",
                mapper(symbol, timeframe), pairs.idOf(symbol), timeframe.name(), Timestamp.from(asOf), count));
        Collections.reverse(desc);
        return desc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> latestOpenTime(Symbol symbol, Timeframe timeframe) {
        Timestamp latest = jdbc.queryForObject("SELECT max(open_time) FROM market_candle WHERE pair_id = ? AND timeframe = ?",
                Timestamp.class, pairs.idOf(symbol), timeframe.name());
        return Optional.ofNullable(latest).map(Timestamp::toInstant);
    }

    private static RowMapper<Candle> mapper(Symbol symbol, Timeframe timeframe) {
        return (rs, i) -> new Candle(symbol, timeframe, rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant(),
                rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6),
                rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getLong(9));
    }
}
