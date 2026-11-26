package com.cryptotrading.market.infrastructure.persistence;

import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.market.domain.SymbolRulesStore;
import com.cryptotrading.shared.domain.Symbol;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
class JpaSymbolRulesStore implements SymbolRulesStore {

    private final PairRegistry pairs;
    private final TradingPairRepository repository;

    JpaSymbolRulesStore(PairRegistry pairs, TradingPairRepository repository) {
        this.pairs = pairs;
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SymbolRules> find(Symbol symbol) {
        return pairs.entity(symbol).filter(e -> e.filtersFetchedAt != null).map(e -> new SymbolRules(symbol,
                e.tradingEnabled, e.tickSize, e.minPrice, e.maxPrice, e.stepSize, e.minQty, e.maxQty,
                e.marketStepSize, e.marketMinQty, e.marketMaxQty, e.minNotional, e.maxNotional, e.filtersFetchedAt));
    }

    @Override
    @Transactional
    public void save(SymbolRules r) {
        TradingPairEntity e = repository.findById(pairs.idOf(r.symbol())).orElseThrow();
        e.tradingEnabled = r.tradingEnabled();
        e.tickSize = r.tickSize();
        e.minPrice = r.minPrice();
        e.maxPrice = r.maxPrice();
        e.stepSize = r.stepSize();
        e.minQty = r.minQuantity();
        e.maxQty = r.maxQuantity();
        e.marketStepSize = r.marketStepSize();
        e.marketMinQty = r.marketMinQuantity();
        e.marketMaxQty = r.marketMaxQuantity();
        e.minNotional = r.minNotional();
        e.maxNotional = r.maxNotional();
        e.filtersFetchedAt = r.fetchedAt();
    }
}
