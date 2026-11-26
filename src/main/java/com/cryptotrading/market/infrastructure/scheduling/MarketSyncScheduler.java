package com.cryptotrading.market.infrastructure.scheduling;

import com.cryptotrading.configuration.ConditionalOnExchangeMode;
import com.cryptotrading.configuration.TradingProperties;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.exchange.domain.StreamSubscription;
import com.cryptotrading.market.application.MarketSyncService;
import com.cryptotrading.shared.domain.Symbol;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Candle synchronisation: the WebSocket stream triggers a REST sync as soon as a candle closes, and a
 * scheduled sync every minute acts as a fallback / gap filler if the stream is down.
 */
@Component
@ConditionalOnExchangeMode
class MarketSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketSyncScheduler.class);

    private final MarketSyncService sync;
    private final ExchangeClient exchange;
    private final TradingProperties properties;
    private final List<StreamSubscription> subscriptions = new ArrayList<>();

    MarketSyncScheduler(MarketSyncService sync, ExchangeClient exchange, TradingProperties properties) {
        this.sync = sync;
        this.exchange = exchange;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void subscribe() {
        for (Symbol symbol : properties.parsedSymbols()) {
            subscriptions.add(exchange.marketDataStream().subscribeClosedCandles(symbol, properties.timeframe(),
                    candle -> syncSafely(symbol)));
        }
    }

    @Scheduled(cron = "${market.sync-cron:15 * * * * *}")
    void scheduledSync() {
        properties.parsedSymbols().forEach(this::syncSafely);
    }

    private void syncSafely(Symbol symbol) {
        try {
            sync.synchronize(symbol, properties.timeframe());
        } catch (RuntimeException e) {
            log.warn("Candle sync failed for {}: {}", symbol, e.getMessage());
        }
    }

    @PreDestroy
    void close() {
        subscriptions.forEach(StreamSubscription::close);
    }
}
