package com.cryptotrading.exchange.domain;

/**
 * Port to a spot exchange. Business modules depend on this interface only, never on an adapter
 * such as Binance. The operations are split by sensitivity so each consumer receives only what it needs:
 * public market data, private read-only account data, and state-changing order operations.
 *
 * <p>Order state is synchronised by REST reconciliation (see {@code docs/BINANCE_INTEGRATION.md}); a private
 * user-data WebSocket stream is not part of the port yet.
 */
public interface ExchangeClient {

    /** Stable identifier of the exchange and environment, e.g. {@code BINANCE_SPOT_TESTNET}. */
    String id();

    MarketDataClient marketData();

    MarketDataStream marketDataStream();

    AccountClient account();

    OrderClient orders();
}
