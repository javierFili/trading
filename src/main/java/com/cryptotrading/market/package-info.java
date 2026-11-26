/**
 * Market module: ingestion, storage and querying of market data (candles, prices). Exposes
 * {@link com.cryptotrading.market.domain.CandleSeries} as the look-ahead-safe input for indicators and
 * strategies. Uses exchange ports for live data; never depends on a concrete exchange adapter.
 */
package com.cryptotrading.market;
