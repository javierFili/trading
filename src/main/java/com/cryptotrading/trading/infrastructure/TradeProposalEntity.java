package com.cryptotrading.trading.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_proposal")
class TradeProposalEntity {
    @Id UUID id;
    @Column(name = "signal_id") UUID signalId;
    @Column(name = "account_id") UUID accountId;
    @Column(name = "pair_id") long pairId;
    @Column(name = "position_id") UUID positionId;
    String purpose;
    String side;
    @Column(name = "reference_price") BigDecimal referencePrice;
    @Column(name = "stop_loss") BigDecimal stopLoss;
    @Column(name = "take_profit") BigDecimal takeProfit;
    BigDecimal quantity;
    BigDecimal notional;
    @Column(name = "risk_amount") BigDecimal riskAmount;
    @Column(name = "est_fees") BigDecimal estFees;
    @Column(name = "reward_risk") BigDecimal rewardRisk;
    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String justification;
    String status;
    @Column(name = "status_reason") String statusReason;
    @Column(name = "content_hash") String contentHash;
    @Column(name = "expires_at") Instant expiresAt;
    @Column(name = "trading_mode") String tradingMode;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;
}
