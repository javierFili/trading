package com.cryptotrading.signals.domain;

/** Published inside the transaction that persisted a new (non-duplicate) actionable signal. */
public record SignalCreatedEvent(Signal signal) {
}
