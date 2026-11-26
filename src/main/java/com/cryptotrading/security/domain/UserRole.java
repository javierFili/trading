package com.cryptotrading.security.domain;

/** VIEWER: read-only. TRADER: may approve/reject proposals, run backtests and reset the circuit breaker. */
public enum UserRole {
    VIEWER,
    TRADER
}
