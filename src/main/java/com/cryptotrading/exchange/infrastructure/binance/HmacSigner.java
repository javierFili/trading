package com.cryptotrading.exchange.infrastructure.binance;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** HMAC-SHA256 request signer (Binance "HMAC" API keys). The secret never leaves this object. */
final class HmacSigner {

    private final SecretKeySpec key;

    HmacSigner(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    @Override
    public String toString() {
        return "HmacSigner[<redacted>]";
    }
}
