/**
 * Execution module (Phase 4): the ONLY module allowed to call
 * {@link com.cryptotrading.exchange.domain.OrderClient}. Re-validates price, balance and risk right before
 * submission, persists the client order id before sending, reconciles unknown outcomes and order updates.
 */
package com.cryptotrading.execution;
