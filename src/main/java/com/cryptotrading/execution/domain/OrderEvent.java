package com.cryptotrading.execution.domain;

import java.util.UUID;

public record OrderEvent(UUID orderId, String symbol, OrderRole role, OrderStatus status, String reason) {
}
