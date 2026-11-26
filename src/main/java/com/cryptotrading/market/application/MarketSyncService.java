package com.cryptotrading.market.application;

import com.cryptotrading.configuration.ConditionalOnExchangeMode;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.market.domain.CandleClosedEvent;
import com.cryptotrading.market.domain.CandleStore;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Keeps stored candles in sync with the exchange (REST), filling gaps after downtime or stream disconnects.
 * Publishes {@link CandleClosedEvent} for the most recent newly stored candle.
 */
@Service
@ConditionalOnExchangeMode
public class MarketSyncService {

    static final int INITIAL_BACKFILL = 500;

    private final ExchangeClient exchange;
    private final CandleStore store;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MarketSyncService(ExchangeClient exchange, CandleStore store, ApplicationEventPublisher events, Clock clock) {
        this.exchange = exchange;
        this.store = store;
        this.events = events;
        this.clock = clock;
    }

    public synchronized int synchronize(Symbol symbol, Timeframe timeframe) {
        Instant now = clock.instant();
        Instant cursor = store.latestOpenTime(symbol, timeframe)
                .map(t -> t.plus(timeframe.duration()))
                .orElse(now.minus(timeframe.duration().multipliedBy(INITIAL_BACKFILL)));
        int inserted = 0;
        Candle lastStored = null;
        while (cursor.isBefore(now)) {
            List<Candle> closed = exchange.marketData().candles(symbol, timeframe, cursor, now, MarketDataService.PAGE)
                    .stream().filter(c -> c.isClosedAt(now)).toList();
            if (closed.isEmpty()) {
                break;
            }
            inserted += store.saveAll(closed, "EXCHANGE");
            lastStored = closed.getLast();
            cursor = lastStored.openTime().plus(timeframe.duration());
        }
        if (inserted > 0 && lastStored != null) {
            events.publishEvent(new CandleClosedEvent(symbol, timeframe, lastStored.openTime(), now));
        }
        return inserted;
    }
}
