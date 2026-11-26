package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.MarketDataStream;
import com.cryptotrading.exchange.domain.StreamSubscription;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/** {@code <symbol>@kline_<interval>} stream; only final (closed, {@code x=true}) candles are delivered. */
final class BinanceKlineStream implements MarketDataStream {

    private final URI streamBase;
    private final HttpClient client;
    private final JsonMapper json;
    private final ScheduledExecutorService scheduler;
    private final Backoff backoff = new Backoff(Duration.ofSeconds(1), Duration.ofMinutes(2));

    BinanceKlineStream(URI streamBase, HttpClient client, JsonMapper json, ScheduledExecutorService scheduler) {
        this.streamBase = streamBase;
        this.client = client;
        this.json = json;
        this.scheduler = scheduler;
    }

    @Override
    public StreamSubscription subscribeClosedCandles(Symbol symbol, Timeframe timeframe, Consumer<Candle> listener) {
        URI uri = URI.create(streamBase + "/ws/" + symbol.code().toLowerCase() + "@kline_" + BinanceMapper.interval(timeframe));
        ReconnectingWebSocket ws = new ReconnectingWebSocket(client, uri,
                message -> parseClosedCandle(json, symbol, timeframe, message).ifPresent(listener),
                () -> { /* consumers back-fill through REST on their own schedule */ },
                backoff, scheduler);
        ws.start();
        return ws;
    }

    static Optional<Candle> parseClosedCandle(JsonMapper json, Symbol symbol, Timeframe timeframe, String message) {
        BinanceDtos.KlineEvent event = json.readValue(message, BinanceDtos.KlineEvent.class);
        if (!"kline".equals(event.eventType()) || event.kline() == null || !event.kline().closed()) {
            return Optional.empty();
        }
        if (!symbol.code().equals(event.kline().symbol())
                || !BinanceMapper.interval(timeframe).equals(event.kline().interval())) {
            return Optional.empty();
        }
        return Optional.of(BinanceMapper.kline(symbol, timeframe, event.kline()));
    }
}
