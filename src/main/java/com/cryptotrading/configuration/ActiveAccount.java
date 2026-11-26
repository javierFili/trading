package com.cryptotrading.configuration;

import com.cryptotrading.shared.domain.TradingMode;

import java.util.Optional;
import java.util.UUID;

/**
 * Exchange account used by the running instance. Accounts are seeded by migration V2; credentials come from
 * environment variables, never from the database. BACKTEST has no account.
 */
public record ActiveAccount(TradingMode mode, Optional<UUID> id) {

    static final UUID TESTNET_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID LIVE_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    public static ActiveAccount forMode(TradingMode mode) {
        return switch (mode) {
            case BACKTEST -> new ActiveAccount(mode, Optional.empty());
            case TESTNET -> new ActiveAccount(mode, Optional.of(TESTNET_ACCOUNT));
            case LIVE -> new ActiveAccount(mode, Optional.of(LIVE_ACCOUNT));
        };
    }

    public UUID requireId() {
        return id.orElseThrow(() -> new IllegalStateException("No exchange account in " + mode + " mode"));
    }
}
