package com.cryptotrading.execution.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trading_order")
class TradingOrderEntity {
    @Id UUID id;
    @Column(name = "proposal_id") UUID proposalId;
    @Column(name = "position_id") UUID positionId;
    @Column(name = "account_id") UUID accountId;
    @Column(name = "pair_id") long pairId;
    String role;
    @Column(name = "client_order_id") String clientOrderId;
    @Column(name = "list_client_order_id") String listClientOrderId;
    @Column(name = "exchange_order_id") String exchangeOrderId;
    String side;
    String type;
    BigDecimal quantity;
    BigDecimal price;
    @Column(name = "stop_price") BigDecimal stopPrice;
    String status;
    @Column(name = "executed_qty") BigDecimal executedQty;
    @Column(name = "cumulative_quote_qty") BigDecimal cumulativeQuoteQty;
    @Column(name = "status_reason") String statusReason;
    @Column(name = "fills_recorded") boolean fillsRecorded;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "last_reconciled_at") Instant lastReconciledAt;
    @Version long version;
}
