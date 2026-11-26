package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.shared.domain.TradingMode;

import java.net.URI;

/**
 * Binance Spot endpoints, bound to the trading mode in code. Base URLs are deliberately NOT configurable,
 * so a TESTNET configuration can never be redirected to production by editing a property.
 *
 * <p>{@link #PUBLIC_DATA} is Binance's market-data-only host: it serves public data and cannot accept
 * orders or account requests, so it is safe for downloading backtest history in any mode.
 *
 * <p>Source: https://github.com/binance/binance-spot-api-docs (REST and WebSocket base endpoints).
 */
public enum BinanceEnvironment {

    TESTNET(URI.create("https://testnet.binance.vision"), URI.create("wss://stream.testnet.binance.vision")),
    PRODUCTION(URI.create("https://api.binance.com"), URI.create("wss://stream.binance.com:9443")),
    PUBLIC_DATA(URI.create("https://data-api.binance.vision"), URI.create("wss://data-stream.binance.vision"));

    private final URI restBaseUri;
    private final URI streamBaseUri;

    BinanceEnvironment(URI restBaseUri, URI streamBaseUri) {
        this.restBaseUri = restBaseUri;
        this.streamBaseUri = streamBaseUri;
    }

    public URI restBaseUri() {
        return restBaseUri;
    }

    public URI streamBaseUri() {
        return streamBaseUri;
    }

    /**
     * Trading environment for a mode. Never returns {@link #PUBLIC_DATA}.
     *
     * @throws IllegalArgumentException for BACKTEST, which must never reach an exchange
     */
    public static BinanceEnvironment forMode(TradingMode mode) {
        return switch (mode) {
            case TESTNET -> TESTNET;
            case LIVE -> PRODUCTION;
            case BACKTEST -> throw new IllegalArgumentException("BACKTEST mode has no exchange environment");
        };
    }
}
