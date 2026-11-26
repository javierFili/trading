package com.cryptotrading.exchange.domain;

import com.cryptotrading.shared.domain.Symbol;

/**
 * State-changing trading operations (signed requests, TRADE).
 *
 * <p>Only the {@code execution} module may use this interface (enforced by architecture tests), and only
 * for proposals that were explicitly approved by an authenticated user. Implementations never retry.
 */
public interface OrderClient {

    OrderCommandResult submit(OrderRequest request);

    OrderCommandResult cancel(Symbol symbol, ClientOrderId clientOrderId);

    OrderListCommandResult submitOco(OcoRequest request);

    OrderListCommandResult cancelOrderList(Symbol symbol, ClientOrderId listClientOrderId);
}
