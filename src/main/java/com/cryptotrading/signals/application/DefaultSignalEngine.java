package com.cryptotrading.signals.application;

import com.cryptotrading.configuration.TradingProperties;
import com.cryptotrading.market.application.MarketDataService;
import com.cryptotrading.market.domain.CandleClosedEvent;
import com.cryptotrading.market.domain.CandleSeries;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.signals.domain.Signal;
import com.cryptotrading.signals.domain.SignalCreatedEvent;
import com.cryptotrading.signals.domain.SignalStore;
import com.cryptotrading.strategies.domain.SignalType;
import com.cryptotrading.strategies.domain.Strategy;
import com.cryptotrading.strategies.domain.StrategyEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultSignalEngine implements SignalEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultSignalEngine.class);

    private final List<Strategy> strategies;
    private final MarketDataService market;
    private final SignalStore store;
    private final ApplicationEventPublisher events;
    private final TradingProperties properties;
    private final Clock clock;

    public DefaultSignalEngine(List<Strategy> strategies, MarketDataService market, SignalStore store,
                               ApplicationEventPublisher events, TradingProperties properties, Clock clock) {
        this.strategies = strategies;
        this.market = market;
        this.store = store;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener
    public void onCandleClosed(CandleClosedEvent event) {
        try {
            evaluate(event.symbol(), event.timeframe(), event.asOf());
        } catch (RuntimeException e) {
            log.error("Signal evaluation failed for {} {}: {}", event.symbol(), event.timeframe(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public List<Signal> evaluate(Symbol symbol, Timeframe timeframe, Instant asOf) {
        List<Signal> created = new ArrayList<>();
        for (Strategy strategy : strategies) {
            if (strategy.descriptor().timeframe() != timeframe) {
                continue;
            }
            long strategyId = store.registerStrategy(strategy.descriptor());
            CandleSeries series = market.closedSeries(symbol, timeframe, asOf, strategy.requiredHistory());
            if (series.isEmpty()) {
                continue;
            }
            StrategyEvaluation evaluation = strategy.evaluate(series);
            if (!evaluation.indicatorValues().isEmpty()) {
                store.saveIndicators(strategyId, symbol, timeframe, evaluation.candleOpenTime(), evaluation.indicatorValues());
            }
            if (evaluation.type() == SignalType.NO_ACTION) {
                continue;
            }
            Signal signal = new Signal(UUID.randomUUID(), symbol, timeframe, strategy.descriptor().id(),
                    strategy.descriptor().version(), evaluation, clock.instant());
            if (store.save(signal, strategyId, properties.mode())) {
                log.info("Signal {} {} {} at {} (score {})", evaluation.type(), symbol, strategy.descriptor().id(),
                        evaluation.candleOpenTime(), evaluation.score().value());
                created.add(signal);
                events.publishEvent(new SignalCreatedEvent(signal));
            }
        }
        return created;
    }
}
