package com.cryptotrading.trading.domain;

import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.shared.domain.BusinessRuleException;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.TradingMode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A trade the system recommends. It is immutable in its economic content; only the status changes.
 * {@link #contentHash()} is a SHA-256 over every economic field: an approval must quote the exact version
 * and hash the user saw, and the hash is re-verified against the stored data before approving.
 */
public final class TradeProposal {

    private final UUID id;
    private final UUID signalId;
    private final UUID accountId;
    private final Symbol symbol;
    private final UUID positionId;
    private final ProposalPurpose purpose;
    private final OrderSide side;
    private final BigDecimal referencePrice;
    private final BigDecimal stopLoss;
    private final BigDecimal takeProfit;
    private final BigDecimal quantity;
    private final BigDecimal notional;
    private final BigDecimal riskAmount;
    private final BigDecimal estimatedFees;
    private final BigDecimal rewardToRisk;
    private final List<String> justification;
    private ProposalStatus status;
    private String statusReason;
    private final String contentHash;
    private final Instant expiresAt;
    private final TradingMode mode;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public TradeProposal(UUID id, UUID signalId, UUID accountId, Symbol symbol, UUID positionId, ProposalPurpose purpose,
                         OrderSide side, BigDecimal referencePrice, BigDecimal stopLoss, BigDecimal takeProfit,
                         BigDecimal quantity, BigDecimal notional, BigDecimal riskAmount, BigDecimal estimatedFees,
                         BigDecimal rewardToRisk, List<String> justification, ProposalStatus status, String statusReason,
                         String contentHash, Instant expiresAt, TradingMode mode, Instant createdAt, Instant updatedAt,
                         long version) {
        this.id = Objects.requireNonNull(id);
        this.signalId = signalId;
        this.accountId = Objects.requireNonNull(accountId);
        this.symbol = Objects.requireNonNull(symbol);
        this.positionId = positionId;
        this.purpose = Objects.requireNonNull(purpose);
        this.side = Objects.requireNonNull(side);
        this.referencePrice = Objects.requireNonNull(referencePrice);
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.quantity = Objects.requireNonNull(quantity);
        this.notional = Objects.requireNonNull(notional);
        this.riskAmount = riskAmount;
        this.estimatedFees = Objects.requireNonNull(estimatedFees);
        this.rewardToRisk = rewardToRisk;
        this.justification = List.copyOf(justification);
        this.status = Objects.requireNonNull(status);
        this.statusReason = statusReason;
        this.contentHash = Objects.requireNonNull(contentHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.mode = Objects.requireNonNull(mode);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static TradeProposal create(UUID signalId, UUID accountId, Symbol symbol, UUID positionId,
                                       ProposalPurpose purpose, OrderSide side, BigDecimal referencePrice,
                                       BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal quantity,
                                       BigDecimal notional, BigDecimal riskAmount, BigDecimal estimatedFees,
                                       BigDecimal rewardToRisk, List<String> justification, Instant now,
                                       Duration ttl, TradingMode mode) {
        if (mode == TradingMode.BACKTEST) {
            throw new IllegalArgumentException("Proposals are not created in BACKTEST mode");
        }
        if ((purpose == ProposalPurpose.ENTRY) != (side == OrderSide.BUY)) {
            throw new IllegalArgumentException("ENTRY must BUY and EXIT must SELL (spot, long only)");
        }
        UUID id = UUID.randomUUID();
        Instant expires = now.plus(ttl);
        String hash = hash(id, symbol, purpose, side, referencePrice, stopLoss, takeProfit, quantity, notional,
                riskAmount, estimatedFees, rewardToRisk, expires, mode);
        return new TradeProposal(id, signalId, accountId, symbol, positionId, purpose, side, referencePrice, stopLoss,
                takeProfit, quantity, notional, riskAmount, estimatedFees, rewardToRisk, justification,
                ProposalStatus.PENDING_APPROVAL, null, hash, expires, mode, now, now, 0);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Binds the approval to the exact version and content the user saw. */
    public void approve(long expectedVersion, String expectedHash, Instant now) {
        requireSameContent(expectedVersion, expectedHash);
        if (isExpired(now)) {
            throw new BusinessRuleException(BusinessRuleException.Kind.GONE, "PROPOSAL_EXPIRED", "Proposal expired at " + expiresAt);
        }
        transition(ProposalStatus.APPROVED, null, now);
    }

    public void reject(long expectedVersion, String expectedHash, String reason, Instant now) {
        requireSameContent(expectedVersion, expectedHash);
        transition(ProposalStatus.REJECTED, reason, now);
    }

    public void expire(Instant now) {
        transition(ProposalStatus.EXPIRED, "Sin ejecutar antes de " + expiresAt, now);
    }

    public void cancel(String reason, Instant now) {
        transition(ProposalStatus.CANCELLED, reason, now);
    }

    public void startExecution(Instant now) {
        if (isExpired(now)) {
            throw new BusinessRuleException(BusinessRuleException.Kind.GONE, "PROPOSAL_EXPIRED", "Approval expired at " + expiresAt);
        }
        transition(ProposalStatus.EXECUTING, null, now);
    }

    public void partiallyFilled(Instant now) {
        transition(ProposalStatus.PARTIALLY_FILLED, null, now);
    }

    public void filled(Instant now) {
        transition(ProposalStatus.FILLED, null, now);
    }

    public void failed(String reason, Instant now) {
        transition(ProposalStatus.FAILED, reason, now);
    }

    public boolean verifyIntegrity() {
        return contentHash.equals(hash(id, symbol, purpose, side, referencePrice, stopLoss, takeProfit, quantity, notional,
                riskAmount, estimatedFees, rewardToRisk, expiresAt, mode));
    }

    private void requireSameContent(long expectedVersion, String expectedHash) {
        if (!verifyIntegrity()) {
            throw new BusinessRuleException(BusinessRuleException.Kind.CONFLICT, "PROPOSAL_INTEGRITY",
                    "Stored proposal does not match its content hash");
        }
        if (expectedVersion != version || !contentHash.equals(expectedHash)) {
            throw new BusinessRuleException(BusinessRuleException.Kind.CONFLICT, "PROPOSAL_CHANGED",
                    "Proposal changed since it was displayed (version/hash mismatch); reload it");
        }
    }

    private void transition(ProposalStatus target, String reason, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException(BusinessRuleException.Kind.CONFLICT, "INVALID_STATUS",
                    "Proposal is " + status + "; cannot become " + target);
        }
        status = target;
        statusReason = reason;
        updatedAt = now;
    }

    static String hash(UUID id, Symbol symbol, ProposalPurpose purpose, OrderSide side, BigDecimal referencePrice,
                       BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal quantity, BigDecimal notional,
                       BigDecimal riskAmount, BigDecimal estimatedFees, BigDecimal rewardToRisk, Instant expiresAt,
                       TradingMode mode) {
        String canonical = String.join("|", id.toString(), symbol.toString(), purpose.name(), side.name(),
                n(referencePrice), n(stopLoss), n(takeProfit), n(quantity), n(notional), n(riskAmount), n(estimatedFees),
                n(rewardToRisk), String.valueOf(expiresAt.toEpochMilli()), mode.name());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String n(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    public UUID id() { return id; }
    public UUID signalId() { return signalId; }
    public UUID accountId() { return accountId; }
    public Symbol symbol() { return symbol; }
    public UUID positionId() { return positionId; }
    public ProposalPurpose purpose() { return purpose; }
    public OrderSide side() { return side; }
    public BigDecimal referencePrice() { return referencePrice; }
    public BigDecimal stopLoss() { return stopLoss; }
    public BigDecimal takeProfit() { return takeProfit; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal notional() { return notional; }
    public BigDecimal riskAmount() { return riskAmount; }
    public BigDecimal estimatedFees() { return estimatedFees; }
    public BigDecimal rewardToRisk() { return rewardToRisk; }
    public List<String> justification() { return justification; }
    public ProposalStatus status() { return status; }
    public String statusReason() { return statusReason; }
    public String contentHash() { return contentHash; }
    public Instant expiresAt() { return expiresAt; }
    public TradingMode mode() { return mode; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
