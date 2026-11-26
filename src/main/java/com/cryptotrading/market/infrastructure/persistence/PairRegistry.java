package com.cryptotrading.market.infrastructure.persistence;

import com.cryptotrading.shared.domain.Symbol;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@link Symbol} ↔ {@code trading_pair.id}. Unknown pairs are registered on first use.
 * Public because other modules' persistence adapters reference pairs by id.
 */
@Component
public class PairRegistry {

    private final TradingPairRepository repository;
    private final Map<Symbol, Long> ids = new ConcurrentHashMap<>();
    private final Map<Long, Symbol> symbols = new ConcurrentHashMap<>();

    public PairRegistry(TradingPairRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public long idOf(Symbol symbol) {
        Long cached = ids.get(symbol);
        if (cached != null) {
            return cached;
        }
        TradingPairEntity entity = repository.findByBaseAssetAndQuoteAsset(symbol.baseAsset(), symbol.quoteAsset())
                .orElseGet(() -> repository.save(new TradingPairEntity(symbol.baseAsset(), symbol.quoteAsset())));
        remember(symbol, entity.getId());
        return entity.getId();
    }

    public Symbol symbolOf(long pairId) {
        Symbol cached = symbols.get(pairId);
        if (cached != null) {
            return cached;
        }
        TradingPairEntity entity = repository.findById(pairId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown pair id " + pairId));
        Symbol symbol = Symbol.of(entity.baseAsset, entity.quoteAsset);
        remember(symbol, pairId);
        return symbol;
    }

    Optional<TradingPairEntity> entity(Symbol symbol) {
        return repository.findByBaseAssetAndQuoteAsset(symbol.baseAsset(), symbol.quoteAsset());
    }

    private void remember(Symbol symbol, long id) {
        ids.put(symbol, id);
        symbols.put(id, symbol);
    }
}
