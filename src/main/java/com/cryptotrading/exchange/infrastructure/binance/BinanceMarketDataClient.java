package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.ExchangeException;
import com.cryptotrading.exchange.domain.HistoricalCandleSource;
import com.cryptotrading.exchange.domain.MarketDataClient;
import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public market data endpoints (no API key). */
final class BinanceMarketDataClient implements MarketDataClient, HistoricalCandleSource {

    static final int MAX_KLINES = 1000;
    private static final TypeReference<List<List<Object>>> KLINES = new TypeReference<>() {
    };

    private final BinanceHttp http;
    private final Clock clock;

    BinanceMarketDataClient(BinanceHttp http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    @Override
    public Instant serverTime() {
        return Instant.ofEpochMilli(http.read("/api/v3/time", Map.of(), 1, false, BinanceHttp.ServerTime.class).serverTime());
    }

    @Override
    public BigDecimal latestPrice(Symbol symbol) {
        return http.read("/api/v3/ticker/price", Map.of("symbol", symbol.code()), 2, false,
                BinanceDtos.TickerPrice.class).price();
    }

    @Override
    public List<Candle> candles(Symbol symbol, Timeframe timeframe, Instant startInclusive, Instant endExclusive, int limit) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.code());
        params.put("interval", BinanceMapper.interval(timeframe));
        params.put("startTime", String.valueOf(startInclusive.toEpochMilli()));
        params.put("endTime", String.valueOf(endExclusive.toEpochMilli() - 1));
        params.put("limit", String.valueOf(Math.min(limit, MAX_KLINES)));
        List<List<Object>> rows = http.read("/api/v3/klines", params, 2, false, KLINES);
        return rows.stream().map(row -> BinanceMapper.kline(symbol, timeframe, row)).toList();
    }

    @Override
    public SymbolRules symbolRules(Symbol symbol) {
        BinanceDtos.ExchangeInfo info = http.read("/api/v3/exchangeInfo", Map.of("symbol", symbol.code()), 2, false,
                BinanceDtos.ExchangeInfo.class);
        return info.symbols().stream()
                .filter(s -> s.symbol().equals(symbol.code()))
                .findFirst()
                .map(s -> BinanceMapper.rules(symbol, s, clock.instant()))
                .orElseThrow(() -> new ExchangeException.Rejected("UNKNOWN_SYMBOL", "Symbol not listed: " + symbol));
    }
}
