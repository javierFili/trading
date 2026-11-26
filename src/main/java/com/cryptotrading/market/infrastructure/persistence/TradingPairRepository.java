package com.cryptotrading.market.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradingPairRepository extends JpaRepository<TradingPairEntity, Long> {

    Optional<TradingPairEntity> findByBaseAssetAndQuoteAsset(String baseAsset, String quoteAsset);
}
