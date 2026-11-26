package com.cryptotrading.execution.domain;

import com.cryptotrading.exchange.domain.ClientOrderId;
import com.cryptotrading.exchange.domain.ExchangeOrder;
import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.exchange.domain.OrderType;
import com.cryptotrading.shared.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Local record of an exchange order. Created BEFORE the order is sent (idempotency key = clientOrderId). */
public final class OrderRecord {

    private final UUID id;
    private final UUID proposalId;
    private UUID positionId;
    private final UUID accountId;
    private final Symbol symbol;
    private final OrderRole role;
    private final ClientOrderId clientOrderId;
    private final ClientOrderId listClientOrderId;
    private String exchangeOrderId;
    private final OrderSide side;
    private final OrderType type;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final BigDecimal stopPrice;
    private OrderStatus status;
    private BigDecimal executedQuantity;
    private BigDecimal cumulativeQuoteQuantity;
    private String statusReason;
    private boolean fillsRecorded;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastReconciledAt;
    private final long version;

    public OrderRecord(UUID id, UUID proposalId, UUID positionId, UUID accountId, Symbol symbol, OrderRole role,
                       ClientOrderId clientOrderId, ClientOrderId listClientOrderId, String exchangeOrderId, OrderSide side,
                       OrderType type, BigDecimal quantity, BigDecimal price, BigDecimal stopPrice, OrderStatus status,
                       BigDecimal executedQuantity, BigDecimal cumulativeQuoteQuantity, String statusReason,
                       boolean fillsRecorded, Instant createdAt, Instant updatedAt, Instant lastReconciledAt, long version) {
        this.id = id;
        this.proposalId = proposalId;
        this.positionId = positionId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.role = role;
        this.clientOrderId = clientOrderId;
        this.listClientOrderId = listClientOrderId;
        this.exchangeOrderId = exchangeOrderId;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.stopPrice = stopPrice;
        this.status = status;
        this.executedQuantity = executedQuantity;
        this.cumulativeQuoteQuantity = cumulativeQuoteQuantity;
        this.statusReason = statusReason;
        this.fillsRecorded = fillsRecorded;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastReconciledAt = lastReconciledAt;
        this.version = version;
    }

    public static OrderRecord pending(UUID proposalId, UUID positionId, UUID accountId, Symbol symbol, OrderRole role,
                                      ClientOrderId clientOrderId, ClientOrderId listClientOrderId, OrderSide side,
                                      OrderType type, BigDecimal quantity, BigDecimal price, BigDecimal stopPrice, Instant now) {
        return new OrderRecord(UUID.randomUUID(), proposalId, positionId, accountId, symbol, role, clientOrderId,
                listClientOrderId, null, side, type, quantity, price, stopPrice, OrderStatus.PENDING_SUBMIT,
                BigDecimal.ZERO, BigDecimal.ZERO, null, false, now, now, null, 0);
    }

    /** Applies the exchange's view. Executed quantities never decrease. */
    public void apply(ExchangeOrder order, Instant now) {
        exchangeOrderId = order.exchangeOrderId();
        status = OrderStatus.of(order.status());
        if (order.executedQuantity() != null && order.executedQuantity().compareTo(executedQuantity) >= 0) {
            executedQuantity = order.executedQuantity();
        }
        if (order.cumulativeQuoteQuantity() != null && order.cumulativeQuoteQuantity().compareTo(cumulativeQuoteQuantity) >= 0) {
            cumulativeQuoteQuantity = order.cumulativeQuoteQuantity();
        }
        updatedAt = now;
        lastReconciledAt = now;
    }

    public void markUnknown(String reason, Instant now) {
        status = OrderStatus.UNKNOWN;
        statusReason = reason;
        updatedAt = now;
    }

    public void markRejected(String reason, Instant now) {
        status = OrderStatus.REJECTED;
        statusReason = reason;
        updatedAt = now;
    }

    public void markExpired(String reason, Instant now) {
        status = OrderStatus.EXPIRED;
        statusReason = reason;
        updatedAt = now;
    }

    public void markFillsRecorded(UUID positionId, Instant now) {
        fillsRecorded = true;
        if (positionId != null) {
            this.positionId = positionId;
        }
        updatedAt = now;
    }

    public boolean hasExecution() {
        return executedQuantity.signum() > 0;
    }

    public UUID id() { return id; }
    public UUID proposalId() { return proposalId; }
    public UUID positionId() { return positionId; }
    public UUID accountId() { return accountId; }
    public Symbol symbol() { return symbol; }
    public OrderRole role() { return role; }
    public ClientOrderId clientOrderId() { return clientOrderId; }
    public ClientOrderId listClientOrderId() { return listClientOrderId; }
    public String exchangeOrderId() { return exchangeOrderId; }
    public OrderSide side() { return side; }
    public OrderType type() { return type; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal price() { return price; }
    public BigDecimal stopPrice() { return stopPrice; }
    public OrderStatus status() { return status; }
    public BigDecimal executedQuantity() { return executedQuantity; }
    public BigDecimal cumulativeQuoteQuantity() { return cumulativeQuoteQuantity; }
    public String statusReason() { return statusReason; }
    public boolean fillsRecorded() { return fillsRecorded; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant lastReconciledAt() { return lastReconciledAt; }
    public long version() { return version; }
}
