package com.cryptotrading.market.domain;

import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.shared.domain.Symbol;

import java.util.Optional;

/** Cached exchange filters per pair (table {@code trading_pair}). */
public interface SymbolRulesStore {

    Optional<SymbolRules> find(Symbol symbol);

    void save(SymbolRules rules);
}
