package com.cryptotrading.shared.domain;

import java.time.Duration;

/** Candle interval. Only fixed-length intervals are supported (no calendar months). */
public enum Timeframe {
    M1(Duration.ofMinutes(1)),
    M5(Duration.ofMinutes(5)),
    M15(Duration.ofMinutes(15)),
    H1(Duration.ofHours(1)),
    H4(Duration.ofHours(4)),
    D1(Duration.ofDays(1));

    private final Duration duration;

    Timeframe(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
