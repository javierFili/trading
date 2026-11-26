package com.cryptotrading.configuration;

import com.cryptotrading.shared.domain.TradingMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast validation of the operating mode, executed at startup before any exchange component is used.
 *
 * <p><b>Phase 0 policy:</b> LIVE is unconditionally locked ({@link #LIVE_SUPPORTED} = false) because the
 * required safety checks (credential validation, withdraw-permission check, user confirmation workflow)
 * are not implemented yet. The remaining checks describe the unlock contract for later phases.
 */
public final class TradingModeGuard {

    /** Flipped only in the phase that implements and tests every LIVE prerequisite. */
    static final boolean LIVE_SUPPORTED = false;

    public static final String LIVE_CONFIRMATION_PHRASE = "I-ACCEPT-REAL-FUNDS-AT-RISK";

    private TradingModeGuard() {
    }

    public static void validate(TradingProperties properties) {
        if (properties.mode() != TradingMode.LIVE) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (!LIVE_SUPPORTED) {
            problems.add("LIVE mode is not available in this build (see docs/SECURITY.md, 'LIVE unlock')");
        }
        if (!properties.live().enabled()) {
            problems.add("trading.live.enabled must be true");
        }
        if (!LIVE_CONFIRMATION_PHRASE.equals(properties.live().confirmation())) {
            problems.add("trading.live.confirmation does not match the required phrase");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start in LIVE mode: " + String.join("; ", problems));
        }
    }
}
