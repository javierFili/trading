package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.ExchangeException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Local request-weight budget per UTC minute, kept in sync with the exchange's {@code X-MBX-USED-WEIGHT-1M}
 * header. When over budget or banned (429/418) it refuses requests instead of sending them.
 */
final class RateLimitGuard {

    private final Clock clock;
    private final int maxWeightPerMinute;
    private long currentMinute = -1;
    private int usedWeight;
    private Instant blockedUntil = Instant.EPOCH;

    RateLimitGuard(Clock clock, int maxWeightPerMinute) {
        this.clock = clock;
        this.maxWeightPerMinute = maxWeightPerMinute;
    }

    synchronized void acquire(int weight) {
        Instant now = clock.instant();
        if (now.isBefore(blockedUntil)) {
            throw new ExchangeException.RateLimited("Exchange requests blocked until " + blockedUntil,
                    Duration.between(now, blockedUntil));
        }
        rollMinute(now);
        if (usedWeight + weight > maxWeightPerMinute) {
            Instant nextMinute = Instant.ofEpochSecond((currentMinute + 1) * 60);
            throw new ExchangeException.RateLimited("Local weight budget exhausted (" + usedWeight + "/"
                    + maxWeightPerMinute + ")", Duration.between(now, nextMinute));
        }
        usedWeight += weight;
    }

    synchronized void observeUsedWeight(int reportedWeight) {
        rollMinute(clock.instant());
        usedWeight = Math.max(usedWeight, reportedWeight);
    }

    /** HTTP 429 (rate limited) or 418 (IP banned): stop all requests for the indicated time. */
    synchronized void block(Duration retryAfter, boolean ipBanned) {
        Duration wait = retryAfter != null ? retryAfter : Duration.ofMinutes(ipBanned ? 5 : 1);
        Instant until = clock.instant().plus(wait);
        if (until.isAfter(blockedUntil)) {
            blockedUntil = until;
        }
    }

    synchronized int usedWeight() {
        return usedWeight;
    }

    private void rollMinute(Instant now) {
        long minute = now.getEpochSecond() / 60;
        if (minute != currentMinute) {
            currentMinute = minute;
            usedWeight = 0;
        }
    }
}
