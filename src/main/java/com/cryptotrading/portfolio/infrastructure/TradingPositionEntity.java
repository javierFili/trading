package com.cryptotrading.portfolio.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trading_position")
class TradingPositionEntity {
    @Id UUID id;
    @Column(name = "account_id") UUID accountId;
    @Column(name = "pair_id") long pairId;
    String status;
    @Column(name = "entry_qty") BigDecimal entryQty;
    @Column(name = "avg_entry_price") BigDecimal avgEntryPrice;
    @Column(name = "entry_quote") BigDecimal entryQuote;
    @Column(name = "exit_qty") BigDecimal exitQty;
    @Column(name = "avg_exit_price") BigDecimal avgExitPrice;
    @Column(name = "exit_quote") BigDecimal exitQuote;
    @Column(name = "stop_loss") BigDecimal stopLoss;
    @Column(name = "take_profit") BigDecimal takeProfit;
    @Column(name = "fees_quote") BigDecimal feesQuote;
    @Column(name = "realized_pnl") BigDecimal realizedPnl;
    @Column(name = "exit_reason") String exitReason;
    @Column(name = "protected") boolean protectedFlag;
    @Column(name = "opened_at") Instant openedAt;
    @Column(name = "closed_at") Instant closedAt;
    @Version long version;
}
