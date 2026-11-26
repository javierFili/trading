package com.cryptotrading.portfolio.domain;

import com.cryptotrading.shared.domain.DecimalPolicy;
import com.cryptotrading.shared.domain.Symbol;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A long spot position. Opened from the entry fill; closed by a protective order, an exit order or manually.
 * {@code realizedPnl = exitQuote − entryQuote − fees} (all in the quote asset).
 */
public final class TradingPosition {

    public enum Status { OPEN, CLOSED }

    private final UUID id;
    private final UUID accountId;
    private final Symbol symbol;
    private Status status;
    private final BigDecimal entryQuantity;
    private final BigDecimal averageEntryPrice;
    private final BigDecimal entryQuote;
    private BigDecimal exitQuantity;
    private BigDecimal averageExitPrice;
    private BigDecimal exitQuote;
    private final BigDecimal stopLoss;
    private final BigDecimal takeProfit;
    private BigDecimal feesQuote;
    private BigDecimal realizedPnl;
    private String exitReason;
    private boolean protectedByExchange;
    private final Instant openedAt;
    private Instant closedAt;
    private final long version;

    public TradingPosition(UUID id, UUID accountId, Symbol symbol, Status status, BigDecimal entryQuantity,
                           BigDecimal averageEntryPrice, BigDecimal entryQuote, BigDecimal exitQuantity,
                           BigDecimal averageExitPrice, BigDecimal exitQuote, BigDecimal stopLoss, BigDecimal takeProfit,
                           BigDecimal feesQuote, BigDecimal realizedPnl, String exitReason, boolean protectedByExchange,
                           Instant openedAt, Instant closedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.accountId = Objects.requireNonNull(accountId);
        this.symbol = Objects.requireNonNull(symbol);
        this.status = Objects.requireNonNull(status);
        this.entryQuantity = DecimalPolicy.requirePositive(entryQuantity, "entryQuantity");
        this.averageEntryPrice = averageEntryPrice;
        this.entryQuote = entryQuote;
        this.exitQuantity = exitQuantity;
        this.averageExitPrice = averageExitPrice;
        this.exitQuote = exitQuote;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.feesQuote = feesQuote;
        this.realizedPnl = realizedPnl;
        this.exitReason = exitReason;
        this.protectedByExchange = protectedByExchange;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.version = version;
    }

    public static TradingPosition open(UUID accountId, Symbol symbol, BigDecimal quantity, BigDecimal entryQuote,
                                       BigDecimal entryFees, BigDecimal stopLoss, BigDecimal takeProfit, Instant at) {
        BigDecimal avg = entryQuote.divide(quantity, DecimalPolicy.CALCULATION).setScale(8, RoundingMode.HALF_EVEN);
        return new TradingPosition(UUID.randomUUID(), accountId, symbol, Status.OPEN, quantity, avg, entryQuote, null,
                null, null, stopLoss, takeProfit, entryFees, null, null, false, at, null, 0);
    }

    public BigDecimal close(BigDecimal quantity, BigDecimal quote, BigDecimal exitFees, String reason, Instant at) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("Position " + id + " is already closed");
        }
        status = Status.CLOSED;
        exitQuantity = quantity;
        exitQuote = quote;
        averageExitPrice = quantity.signum() > 0
                ? quote.divide(quantity, DecimalPolicy.CALCULATION).setScale(8, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
        feesQuote = feesQuote.add(exitFees);
        realizedPnl = quote.subtract(entryQuote).subtract(feesQuote);
        exitReason = reason;
        closedAt = at;
        protectedByExchange = false;
        return realizedPnl;
    }

    public void markProtected(boolean value) {
        protectedByExchange = value;
    }

    public UUID id() { return id; }
    public UUID accountId() { return accountId; }
    public Symbol symbol() { return symbol; }
    public Status status() { return status; }
    public BigDecimal entryQuantity() { return entryQuantity; }
    public BigDecimal averageEntryPrice() { return averageEntryPrice; }
    public BigDecimal entryQuote() { return entryQuote; }
    public BigDecimal exitQuantity() { return exitQuantity; }
    public BigDecimal averageExitPrice() { return averageExitPrice; }
    public BigDecimal exitQuote() { return exitQuote; }
    public BigDecimal stopLoss() { return stopLoss; }
    public BigDecimal takeProfit() { return takeProfit; }
    public BigDecimal feesQuote() { return feesQuote; }
    public BigDecimal realizedPnl() { return realizedPnl; }
    public String exitReason() { return exitReason; }
    public boolean protectedByExchange() { return protectedByExchange; }
    public Instant openedAt() { return openedAt; }
    public Instant closedAt() { return closedAt; }
    public long version() { return version; }
}
