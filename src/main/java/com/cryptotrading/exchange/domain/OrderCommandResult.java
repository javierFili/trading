package com.cryptotrading.exchange.domain;

/**
 * Outcome of a state-changing order command (submit / cancel).
 *
 * <p>{@link Unknown} is a first-class result: a timeout or 5xx does NOT mean the order failed, nor that it
 * succeeded. The caller must keep the operation in an in-flight state and reconcile it by querying the
 * order with its {@link ClientOrderId}. It must never blindly retry with a new id.
 */
public sealed interface OrderCommandResult {

    ClientOrderId clientOrderId();

    /** The exchange acknowledged the command. The order may still be NEW / partially filled. */
    record Accepted(ClientOrderId clientOrderId, ExchangeOrder order) implements OrderCommandResult {
    }

    /** The exchange definitively rejected the command; nothing changed on the exchange. */
    record Rejected(ClientOrderId clientOrderId, String errorCode, String message) implements OrderCommandResult {
    }

    /** The outcome is not known. Reconciliation is mandatory. */
    record Unknown(ClientOrderId clientOrderId, String reason) implements OrderCommandResult {
    }
}
