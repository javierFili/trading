package com.cryptotrading.security.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238, Appendix B (SHA-1, secret "12345678901234567890", 8 digits). */
    @ParameterizedTest
    @CsvSource({"59,94287082", "1111111109,07081804", "1111111111,14050471", "1234567890,89005924",
            "2000000000,69279037", "20000000000,65353130"})
    void rfc6238TestVectors(long unixSeconds, String expected) {
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.code(secret, Totp.step(Instant.ofEpochSecond(unixSeconds)), 8)).isEqualTo(expected);
    }

    @Test
    void verifyAcceptsAdjacentStepsOnlyAndReturnsMatchedStep() {
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        Instant now = Instant.ofEpochSecond(1_000_000_000L);
        long step = Totp.step(now);
        assertThat(Totp.verify(secret, Totp.code(secret, step - 1, 6), now)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.code(secret, step + 1, 6), now)).hasValue(step + 1);
        assertThat(Totp.verify(secret, Totp.code(secret, step + 2, 6), now)).isEmpty();
        assertThat(Totp.verify(secret, "12345", now)).isEmpty();
        assertThat(Totp.verify(secret, null, now)).isEmpty();
    }

    @Test
    void base32RoundTripAndRfc4648Vector() {
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        byte[] random = new byte[20];
        new java.security.SecureRandom().nextBytes(random);
        assertThat(Base32.decode(Base32.encode(random))).isEqualTo(random);
    }
}
