package com.cryptotrading.shared.domain;

/**
 * Operating mode of the whole platform. Exactly one mode is active per running instance.
 *
 * <ul>
 *   <li>{@link #BACKTEST}: historical data and simulated fills only. No exchange connectivity for orders.</li>
 *   <li>{@link #TESTNET}: Binance Spot Testnet, virtual funds.</li>
 *   <li>{@link #LIVE}: Binance Spot production, real funds. Locked; see {@code docs/SECURITY.md}.</li>
 * </ul>
 */
public enum TradingMode {
    BACKTEST,
    TESTNET,
    LIVE;

    public boolean usesRealFunds() {
        return this == LIVE;
    }
}
