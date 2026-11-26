package com.cryptotrading.execution.domain;

import com.cryptotrading.exchange.domain.ClientOrderId;

import java.util.UUID;

/** {@code cts} + role code + 32 hex chars = 36 characters, unique and traceable. */
public final class ClientOrderIds {

    private ClientOrderIds() {
    }

    public static ClientOrderId next(char roleCode) {
        return new ClientOrderId("cts" + roleCode + UUID.randomUUID().toString().replace("-", ""));
    }
}
