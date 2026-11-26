package com.cryptotrading.shared.domain;

import java.math.BigDecimal;
import java.time.Instant;

public final class CandleFixtures {

    public static final Symbol BTC_USDT = Symbol.of("BTC", "USDT");

    private CandleFixtures() {
    }

    /** 1h candle opening at {@code openTime} with flat OHLC = {@code price}. */
    public static Candle hourly(Instant openTime, String price) {
        BigDecimal p = new BigDecimal(price);
        return new Candle(BTC_USDT, Timeframe.H1, openTime,
                openTime.plus(Timeframe.H1.duration()).minusMillis(1),
                p, p, p, p, BigDecimal.ONE, p, 10);
    }
}
