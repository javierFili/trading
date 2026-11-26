package com.cryptotrading.exchange.infrastructure.binance;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Exponential backoff with "equal jitter": delay in [d/2, d], d = min(cap, base * 2^attempt). */
public record Backoff(Duration base, Duration cap) {

    public Duration delay(int attempt) {
        long baseMs = base.toMillis();
        long capped = attempt >= 30 ? cap.toMillis() : Math.min(cap.toMillis(), baseMs << attempt);
        long half = capped / 2;
        return Duration.ofMillis(half + ThreadLocalRandom.current().nextLong(half + 1));
    }
}
