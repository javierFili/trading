package com.cryptotrading.market.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trading_pair")
public class TradingPairEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "base_asset") String baseAsset;
    @Column(name = "quote_asset") String quoteAsset;
    @Column(name = "symbol_code") String symbolCode;
    boolean enabled;
    @Column(name = "trading_enabled") boolean tradingEnabled;
    @Column(name = "tick_size") BigDecimal tickSize;
    @Column(name = "min_price") BigDecimal minPrice;
    @Column(name = "max_price") BigDecimal maxPrice;
    @Column(name = "step_size") BigDecimal stepSize;
    @Column(name = "min_qty") BigDecimal minQty;
    @Column(name = "max_qty") BigDecimal maxQty;
    @Column(name = "market_step_size") BigDecimal marketStepSize;
    @Column(name = "market_min_qty") BigDecimal marketMinQty;
    @Column(name = "market_max_qty") BigDecimal marketMaxQty;
    @Column(name = "min_notional") BigDecimal minNotional;
    @Column(name = "max_notional") BigDecimal maxNotional;
    @Column(name = "filters_fetched_at") Instant filtersFetchedAt;

    protected TradingPairEntity() {
    }

    TradingPairEntity(String baseAsset, String quoteAsset) {
        this.baseAsset = baseAsset;
        this.quoteAsset = quoteAsset;
        this.symbolCode = baseAsset + quoteAsset;
        this.enabled = true;
        this.tradingEnabled = true;
    }

    public Long getId() {
        return id;
    }
}
