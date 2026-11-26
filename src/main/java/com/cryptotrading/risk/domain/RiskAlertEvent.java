package com.cryptotrading.risk.domain;

import java.util.UUID;

/** Risk-relevant event for notification (circuit breaker changes, unprotected positions, rejections). */
public record RiskAlertEvent(UUID accountId, String rule, String severity, String detail) {
}
