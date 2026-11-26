package com.cryptotrading.exchange.domain;

import java.util.List;

/** Outcome of an order-list command (OCO). Same semantics as {@link OrderCommandResult}. */
public sealed interface OrderListCommandResult {

    ClientOrderId listClientOrderId();

    record Accepted(ClientOrderId listClientOrderId, List<ExchangeOrder> orders) implements OrderListCommandResult {
        public Accepted {
            orders = List.copyOf(orders);
        }
    }

    record Rejected(ClientOrderId listClientOrderId, String errorCode, String message) implements OrderListCommandResult {
    }

    record Unknown(ClientOrderId listClientOrderId, String reason) implements OrderListCommandResult {
    }
}
