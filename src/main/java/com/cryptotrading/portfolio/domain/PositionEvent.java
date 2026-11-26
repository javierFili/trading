package com.cryptotrading.portfolio.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when a position is opened or closed. */
public record PositionEvent(UUID positionId, String symbol, String type, BigDecimal realizedPnl, String reason) {
}
