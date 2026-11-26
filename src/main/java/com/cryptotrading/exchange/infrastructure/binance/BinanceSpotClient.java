package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.AccountClient;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.exchange.domain.MarketDataClient;
import com.cryptotrading.exchange.domain.MarketDataStream;
import com.cryptotrading.exchange.domain.OrderClient;

/** {@link ExchangeClient} for Binance Spot (TESTNET or PRODUCTION, fixed at construction). */
public final class BinanceSpotClient implements ExchangeClient, AutoCloseable {

    private final BinanceEnvironment environment;
    private final BinanceMarketDataClient marketData;
    private final BinanceKlineStream stream;
    private final BinanceAccountClient account;
    private final BinanceOrderClient orders;
    private final java.util.concurrent.ScheduledExecutorService streamScheduler;

    BinanceSpotClient(BinanceEnvironment environment, BinanceMarketDataClient marketData, BinanceKlineStream stream,
                      BinanceAccountClient account, BinanceOrderClient orders,
                      java.util.concurrent.ScheduledExecutorService streamScheduler) {
        this.environment = environment;
        this.marketData = marketData;
        this.stream = stream;
        this.account = account;
        this.orders = orders;
        this.streamScheduler = streamScheduler;
    }

    @Override
    public void close() {
        streamScheduler.shutdownNow();
    }

    @Override
    public String id() {
        return "BINANCE_SPOT_" + environment.name();
    }

    @Override
    public MarketDataClient marketData() {
        return marketData;
    }

    @Override
    public MarketDataStream marketDataStream() {
        return stream;
    }

    @Override
    public AccountClient account() {
        return account;
    }

    @Override
    public OrderClient orders() {
        return orders;
    }

    BinanceAccountClient binanceAccount() {
        return account;
    }
}
