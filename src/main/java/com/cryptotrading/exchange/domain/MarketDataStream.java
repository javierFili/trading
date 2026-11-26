package com.cryptotrading.exchange.domain;

import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;

import java.util.function.Consumer;

/** Real-time public market data (WebSocket). */
public interface MarketDataStream {

    /** Delivers each candle once it is final (closed). After a reconnect, gaps must be back-filled via REST. */
    StreamSubscription subscribeClosedCandles(Symbol symbol, Timeframe timeframe, Consumer<Candle> listener);
}
