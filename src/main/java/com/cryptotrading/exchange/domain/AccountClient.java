package com.cryptotrading.exchange.domain;

import com.cryptotrading.shared.domain.Symbol;

import java.util.List;
import java.util.Optional;

/** Private, read-only account data (signed requests, USER_DATA). Never changes exchange state. */
public interface AccountClient {

    List<Balance> balances();

    /**
     * Used by reconciliation. {@code Optional.empty()} only when the exchange explicitly answers that the
     * order does not exist; transport failures throw {@link ExchangeException}.
     */
    Optional<ExchangeOrder> findOrder(Symbol symbol, ClientOrderId clientOrderId);

    List<ExchangeOrder> openOrders(Symbol symbol);

    /** Trades (fills) of one order, with real commissions. */
    List<Fill> fills(Symbol symbol, String exchangeOrderId);
}
