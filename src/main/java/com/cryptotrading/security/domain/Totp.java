package com.cryptotrading.security.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

/** RFC 6238 TOTP (HMAC-SHA1, 30 s steps). Verification accepts ±1 step and returns the matched step. */
public final class Totp {

    public static final long STEP_SECONDS = 30;
    public static final int DIGITS = 6;

    private Totp() {
    }

    public static long step(Instant instant) {
        return instant.getEpochSecond() / STEP_SECONDS;
    }

    public static String code(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            int mod = 1;
            for (int i = 0; i < digits; i++) {
                mod *= 10;
            }
            return String.format("%0" + digits + "d", binary % mod);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /** @return the matching step if {@code code} is valid at {@code now} ± 1 step */
    public static OptionalLong verify(byte[] secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = step(now);
        for (long s = current - 1; s <= current + 1; s++) {
            if (MessageDigest.isEqual(code(secret, s, DIGITS).getBytes(), code.getBytes())) {
                return OptionalLong.of(s);
            }
        }
        return OptionalLong.empty();
    }
}
