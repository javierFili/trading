package com.cryptotrading.risk.domain;

/**
 * Independent gatekeeper. Evaluated when a proposal is created AND again immediately before execution.
 * Pure function of its inputs: no I/O, no clock access (time comes in {@link RiskContext}).
 */
public interface RiskManager {

    RiskDecision evaluate(TradeIntent intent, RiskLimits limits, RiskContext context);
}
