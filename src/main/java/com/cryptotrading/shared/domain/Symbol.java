package com.cryptotrading.shared.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A spot trading pair, e.g. BTC/USDT. Exchange-neutral: adapters translate it to their own format.
 */
public record Symbol(String baseAsset, String quoteAsset) {

    private static final Pattern ASSET = Pattern.compile("^[A-Z0-9]{2,20}$");

    public Symbol {
        Objects.requireNonNull(baseAsset, "baseAsset");
        Objects.requireNonNull(quoteAsset, "quoteAsset");
        if (!ASSET.matcher(baseAsset).matches() || !ASSET.matcher(quoteAsset).matches()) {
            throw new IllegalArgumentException("Invalid asset code: " + baseAsset + "/" + quoteAsset);
        }
        if (baseAsset.equals(quoteAsset)) {
            throw new IllegalArgumentException("Base and quote asset must differ: " + baseAsset);
        }
    }

    public static Symbol of(String baseAsset, String quoteAsset) {
        return new Symbol(baseAsset, quoteAsset);
    }

    /** Parses {@code "BTC/USDT"}. */
    public static Symbol parse(String value) {
        String[] parts = value.trim().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Symbol must look like BASE/QUOTE: " + value);
        }
        return new Symbol(parts[0], parts[1]);
    }

    /** Concatenated code, e.g. {@code BTCUSDT}. */
    public String code() {
        return baseAsset + quoteAsset;
    }

    @Override
    public String toString() {
        return baseAsset + "/" + quoteAsset;
    }
}
