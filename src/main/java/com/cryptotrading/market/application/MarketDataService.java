package com.cryptotrading.market.application;

import com.cryptotrading.exchange.domain.HistoricalCandleSource;
import com.cryptotrading.market.domain.BinanceCsvParser;
import com.cryptotrading.market.domain.CandleSeries;
import com.cryptotrading.market.domain.CandleStore;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Reader;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Read access to stored candles and import of historical data (CSV or market-data-only endpoint). */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    static final int PAGE = 1000;

    private final CandleStore store;
    private final HistoricalCandleSource historical;
    private final Clock clock;

    public MarketDataService(CandleStore store, HistoricalCandleSource historical, Clock clock) {
        this.store = store;
        this.historical = historical;
        this.clock = clock;
    }

    /** Look-ahead-safe input for strategies: only candles closed at {@code asOf}. */
    public CandleSeries closedSeries(Symbol symbol, Timeframe timeframe, Instant asOf, int count) {
        return CandleSeries.of(symbol, timeframe, asOf, store.findLatestClosed(symbol, timeframe, asOf, count));
    }

    public List<Candle> range(Symbol symbol, Timeframe timeframe, Instant from, Instant to) {
        return store.findRange(symbol, timeframe, from, to);
    }

    public int importCsv(Symbol symbol, Timeframe timeframe, Reader csv) {
        Instant now = clock.instant();
        List<Candle> closed = BinanceCsvParser.parse(symbol, timeframe, csv).stream()
                .filter(c -> c.isClosedAt(now)).toList();
        int inserted = store.saveAll(closed, "CSV");
        log.info("Imported {} new {} {} candles from CSV ({} rows)", inserted, symbol, timeframe, closed.size());
        return inserted;
    }

    /** Downloads closed candles in {@code [from, to)} from the public market-data host. */
    public int downloadHistory(Symbol symbol, Timeframe timeframe, Instant from, Instant to) {
        Instant now = clock.instant();
        Instant end = to.isAfter(now) ? now : to;
        Instant cursor = from;
        int inserted = 0;
        while (cursor.isBefore(end)) {
            List<Candle> page = historical.candles(symbol, timeframe, cursor, end, PAGE);
            if (page.isEmpty()) {
                break;
            }
            inserted += store.saveAll(page.stream().filter(c -> c.isClosedAt(now)).toList(), "BINANCE_PUBLIC");
            cursor = page.getLast().openTime().plus(timeframe.duration());
        }
        log.info("Downloaded history {} {} [{} - {}): {} new candles", symbol, timeframe, from, end, inserted);
        return inserted;
    }
}
