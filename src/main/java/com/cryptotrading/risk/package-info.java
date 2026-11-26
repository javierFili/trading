/**
 * Risk module: position sizing, stop loss / take profit validation, daily loss limit, exposure, reserve,
 * fee/slippage estimation, exchange minimums and circuit breaker. Must not depend on strategies or
 * signals: risk limits can never be influenced by the component that proposes the trade.
 */
package com.cryptotrading.risk;
