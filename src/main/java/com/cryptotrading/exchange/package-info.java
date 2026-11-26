/**
 * Exchange module. {@code domain} holds exchange-neutral ports and models
 * ({@link com.cryptotrading.exchange.domain.ExchangeClient}); {@code infrastructure.binance} holds the only
 * Binance-specific code. No class outside this module may depend on the Binance adapter.
 */
package com.cryptotrading.exchange;
