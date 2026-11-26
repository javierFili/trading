package com.cryptotrading.portfolio.application;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.exchange.domain.Balance;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.portfolio.domain.PositionEvent;
import com.cryptotrading.portfolio.domain.PositionRepository;
import com.cryptotrading.portfolio.domain.TradingPosition;
import com.cryptotrading.risk.application.CircuitBreakerService;
import com.cryptotrading.risk.application.RiskProperties;
import com.cryptotrading.shared.domain.Symbol;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PortfolioService {

    private final PositionRepository positions;
    private final ObjectProvider<ExchangeClient> exchange;
    private final CircuitBreakerService breaker;
    private final RiskProperties risk;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PortfolioService(PositionRepository positions, ObjectProvider<ExchangeClient> exchange,
                            CircuitBreakerService breaker, RiskProperties risk, ApplicationEventPublisher events, Clock clock) {
        this.positions = positions;
        this.exchange = exchange;
        this.breaker = breaker;
        this.risk = risk;
        this.events = events;
        this.clock = clock;
    }

    /** Exchange balances (empty in BACKTEST). Always fresh: never cached for risk decisions. */
    public List<Balance> balances() {
        ExchangeClient client = exchange.getIfAvailable();
        return client == null ? List.of() : client.account().balances();
    }

    public BigDecimal freeBalance(String asset) {
        return balances().stream().filter(b -> b.asset().equals(asset)).map(Balance::free).findFirst().orElse(BigDecimal.ZERO);
    }

    public Optional<TradingPosition> openPosition(UUID accountId, Symbol symbol) {
        return positions.findOpen(accountId, symbol);
    }

    public int openPositionCount(UUID accountId) {
        return positions.countOpen(accountId);
    }

    /** Realized loss of the current UTC day as a positive number (0 when the day is in profit). */
    public BigDecimal lossToday(UUID accountId) {
        Instant startOfDay = LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal pnl = positions.realizedPnlSince(accountId, startOfDay);
        return pnl.signum() < 0 ? pnl.negate() : BigDecimal.ZERO;
    }

    @Transactional
    public TradingPosition open(UUID accountId, Symbol symbol, BigDecimal quantity, BigDecimal entryQuote,
                                BigDecimal entryFees, BigDecimal stopLoss, BigDecimal takeProfit, String actor) {
        TradingPosition position = TradingPosition.open(accountId, symbol, quantity, entryQuote, entryFees, stopLoss,
                takeProfit, clock.instant());
        positions.save(position);
        events.publishEvent(new PositionEvent(position.id(), symbol.toString(), "OPENED", null, null));
        events.publishEvent(new AuditEvent(actor, "POSITION_OPENED", "position", position.id().toString(),
                Map.of("symbol", symbol.toString(), "quantity", quantity.toPlainString(), "entryQuote", entryQuote.toPlainString())));
        return position;
    }

    @Transactional
    public void markProtected(UUID positionId, boolean value) {
        TradingPosition position = positions.find(positionId).orElseThrow();
        position.markProtected(value);
        positions.save(position);
    }

    /** Closes the position and trips the circuit breaker if the daily loss limit is reached. */
    @Transactional
    public BigDecimal close(UUID positionId, BigDecimal quantity, BigDecimal exitQuote, BigDecimal exitFees,
                            String reason, String actor) {
        TradingPosition position = positions.find(positionId).orElseThrow();
        BigDecimal pnl = position.close(quantity, exitQuote, exitFees, reason, clock.instant());
        positions.save(position);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("realizedPnl", pnl.toPlainString());
        events.publishEvent(new AuditEvent(actor, "POSITION_CLOSED", "position", positionId.toString(), details));
        events.publishEvent(new PositionEvent(positionId, position.symbol().toString(), "CLOSED", pnl, reason));
        BigDecimal loss = lossToday(position.accountId());
        if (loss.compareTo(risk.maxDailyLoss()) >= 0) {
            breaker.open(position.accountId(), "Pérdida diaria " + loss.toPlainString() + " ≥ límite "
                    + risk.maxDailyLoss().toPlainString(), "system:portfolio");
        }
        return pnl;
    }

    public Optional<TradingPosition> find(UUID id) {
        return positions.find(id);
    }

    public List<TradingPosition> recent(UUID accountId, int limit) {
        return positions.recent(accountId, limit);
    }
}
