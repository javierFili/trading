package com.cryptotrading.market.application;

import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.market.domain.SymbolRulesStore;
import com.cryptotrading.shared.domain.Symbol;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * Exchange filters for a symbol. With an exchange connection, cached rules older than {@link #REFRESH_AFTER}
 * are refreshed; in BACKTEST the stored rules are used as-is.
 */
@Service
public class SymbolRulesProvider {

    static final Duration REFRESH_AFTER = Duration.ofHours(1);

    private final SymbolRulesStore store;
    private final ObjectProvider<ExchangeClient> exchange;
    private final Clock clock;

    public SymbolRulesProvider(SymbolRulesStore store, ObjectProvider<ExchangeClient> exchange, Clock clock) {
        this.store = store;
        this.exchange = exchange;
        this.clock = clock;
    }

    public SymbolRules rules(Symbol symbol) {
        SymbolRules cached = store.find(symbol).orElse(null);
        ExchangeClient client = exchange.getIfAvailable();
        if (client != null && (cached == null || cached.isStale(clock.instant(), REFRESH_AFTER))) {
            SymbolRules fresh = client.marketData().symbolRules(symbol);
            store.save(fresh);
            return fresh;
        }
        if (cached == null) {
            throw new IllegalStateException("No trading rules stored for " + symbol);
        }
        return cached;
    }
}
