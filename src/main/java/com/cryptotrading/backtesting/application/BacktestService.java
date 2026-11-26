package com.cryptotrading.backtesting.application;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.backtesting.domain.BacktestConfig;
import com.cryptotrading.backtesting.domain.BacktestEngine;
import com.cryptotrading.backtesting.domain.BacktestResult;
import com.cryptotrading.backtesting.domain.BacktestRun;
import com.cryptotrading.backtesting.domain.BacktestRun.DatasetSplit;
import com.cryptotrading.backtesting.domain.BacktestStore;
import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.market.application.MarketDataService;
import com.cryptotrading.market.application.SymbolRulesProvider;
import com.cryptotrading.risk.application.RiskProperties;
import com.cryptotrading.risk.domain.RiskManager;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.strategies.domain.Strategy;
import com.cryptotrading.strategies.domain.StrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs backtests asynchronously on stored candles. With {@code split=true} the period is cut chronologically
 * into TRAIN (60 %), VALIDATION (20 %) and TEST (20 %) segments, each simulated independently; earlier data
 * is used only as indicator warm-up. Every run stores parameters, limits, costs and a data checksum.
 */
@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);
    static final Duration MAX_RANGE = Duration.ofDays(3 * 366);

    public record BacktestRequest(Symbol symbol, Timeframe timeframe, Instant from, Instant to, String strategyCode,
                                  Map<String, String> parameters, boolean split, BigDecimal initialCapital) {
    }

    private record Segment(DatasetSplit split, Instant start, Instant end, UUID runId) {
    }

    private final MarketDataService market;
    private final SymbolRulesProvider rulesProvider;
    private final RiskManager riskManager;
    private final RiskProperties risk;
    private final BacktestStore store;
    /** Dedicated executor: backtests never compete with request or execution threads for a shared pool. */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final BacktestEngine engine = new BacktestEngine();

    public BacktestService(MarketDataService market, SymbolRulesProvider rulesProvider, RiskManager riskManager,
                           RiskProperties risk, BacktestStore store, TransactionTemplate tx,
                           ApplicationEventPublisher events, Clock clock) {
        this.market = market;
        this.rulesProvider = rulesProvider;
        this.riskManager = riskManager;
        this.risk = risk;
        this.store = store;
        this.tx = tx;
        this.events = events;
        this.clock = clock;
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Validates and registers the runs, then simulates them in the background. Returns the group id. */
    public UUID start(BacktestRequest request, String user) {
        if (!request.to().isAfter(request.from())) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
        if (Duration.between(request.from(), request.to()).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("Maximum backtest range is 3 years");
        }
        Strategy strategy = StrategyFactory.create(request.strategyCode(), request.timeframe(), request.parameters());
        BigDecimal capital = request.initialCapital() != null ? request.initialCapital() : risk.initialCapital();
        UUID groupId = UUID.randomUUID();
        List<Segment> segments = segments(request);
        Map<String, Object> limits = limitsSnapshot();
        tx.executeWithoutResult(s -> {
            for (Segment seg : segments) {
                store.create(new BacktestRun(seg.runId(), groupId, strategy.descriptor().id(), strategy.descriptor().version(),
                        strategy.descriptor().parameters(), request.symbol().toString(), request.timeframe().name(),
                        seg.start(), seg.end(), seg.split(), risk.feeRate(), risk.slippageRate(), capital, "RUNNING",
                        null, null, null, null, null, user, clock.instant(), null), limits);
            }
            events.publishEvent(new AuditEvent(user, "BACKTEST_STARTED", "backtest_group", groupId.toString(),
                    Map.of("strategy", strategy.descriptor().version(), "symbol", request.symbol().toString(),
                            "from", request.from().toString(), "to", request.to().toString())));
        });
        executor.execute(() -> segments.forEach(seg -> runSegment(seg, request, strategy, capital)));
        return groupId;
    }

    private void runSegment(Segment seg, BacktestRequest request, Strategy strategy, BigDecimal capital) {
        try {
            Instant warmupStart = seg.start().minus(request.timeframe().duration().multipliedBy(strategy.requiredHistory()));
            List<Candle> candles = market.range(request.symbol(), request.timeframe(), warmupStart, seg.end());
            long inSegment = candles.stream().filter(c -> !c.openTime().isBefore(seg.start())).count();
            if (inSegment == 0) {
                throw new IllegalStateException("No stored candles in " + seg.start() + " – " + seg.end()
                        + ". Import or download history first.");
            }
            long expected = Duration.between(seg.start(), seg.end()).dividedBy(request.timeframe().duration());
            int gaps = (int) Math.max(0, expected - inSegment);
            SymbolRules rules = rulesProvider.rules(request.symbol());
            BacktestResult result = engine.run(candles, new BacktestConfig(strategy, riskManager, risk.limits(), rules,
                    risk.feeRate(), risk.slippageRate(), risk.stopLimitBuffer(), capital, seg.start(), seg.end()));
            store.complete(seg.runId(), result.metrics(), gaps, result.trades(), checksum(candles), candles.size());
            log.info("Backtest {} {} finished: {} trades, net {}", seg.runId(), seg.split(), result.metrics().trades(),
                    result.metrics().netProfit());
        } catch (RuntimeException e) {
            log.warn("Backtest {} failed: {}", seg.runId(), e.getMessage());
            store.fail(seg.runId(), e.getMessage());
        }
    }

    static List<Segment> segments(BacktestRequest r) {
        List<Segment> result = new ArrayList<>();
        if (!r.split()) {
            result.add(new Segment(DatasetSplit.FULL, r.from(), r.to(), UUID.randomUUID()));
            return result;
        }
        Duration total = Duration.between(r.from(), r.to());
        Instant trainEnd = align(r.from().plus(total.multipliedBy(60).dividedBy(100)), r.timeframe());
        Instant validationEnd = align(r.from().plus(total.multipliedBy(80).dividedBy(100)), r.timeframe());
        result.add(new Segment(DatasetSplit.TRAIN, r.from(), trainEnd, UUID.randomUUID()));
        result.add(new Segment(DatasetSplit.VALIDATION, trainEnd, validationEnd, UUID.randomUUID()));
        result.add(new Segment(DatasetSplit.TEST, validationEnd, r.to(), UUID.randomUUID()));
        return result;
    }

    private static Instant align(Instant instant, Timeframe timeframe) {
        long step = timeframe.duration().toMillis();
        return Instant.ofEpochMilli(instant.toEpochMilli() / step * step);
    }

    private Map<String, Object> limitsSnapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxRiskPerTrade", risk.maxRiskPerTrade().toPlainString());
        m.put("maxDailyLoss", risk.maxDailyLoss().toPlainString());
        m.put("minQuoteReserve", risk.minQuoteReserve().toPlainString());
        m.put("maxPositionNotional", risk.maxPositionNotional().toPlainString());
        m.put("minRewardToRisk", risk.minRewardToRisk().toPlainString());
        m.put("stopLimitBuffer", risk.stopLimitBuffer().toPlainString());
        return m;
    }

    static String checksum(List<Candle> candles) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Candle c : candles) {
                digest.update((c.openTime().toEpochMilli() + "|" + c.open().toPlainString() + "|" + c.high().toPlainString()
                        + "|" + c.low().toPlainString() + "|" + c.close().toPlainString() + "|" + c.volume().toPlainString()
                        + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
