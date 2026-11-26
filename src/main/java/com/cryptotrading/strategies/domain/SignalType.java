package com.cryptotrading.strategies.domain;

/** Spot-only signal types: there is no short selling, so SELL always means closing a long position. */
public enum SignalType {
    ENTRY_LONG,
    EXIT_LONG,
    NO_ACTION
}
