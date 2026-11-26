package com.cryptotrading.security.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    static SecurityProperties props(String masterKey) {
        return new SecurityProperties("unused", Duration.ofMinutes(30), masterKey, 5, Duration.ofMinutes(15), null);
    }

    private final SecretCipher cipher = new SecretCipher(props(Base64.getEncoder().encodeToString(new byte[32])));

    @Test
    void roundTripWithRandomIv() {
        byte[] plain = "totp-secret".getBytes(StandardCharsets.UTF_8);
        byte[] a = cipher.encrypt(plain);
        byte[] b = cipher.encrypt(plain);
        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo(plain);
    }

    @Test
    void tamperingIsDetected() {
        byte[] data = cipher.encrypt("secret".getBytes(StandardCharsets.UTF_8));
        data[data.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(data)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyLengthIsRejectedAtStartup() {
        assertThatThrownBy(() -> new SecretCipher(props(Base64.getEncoder().encodeToString(new byte[16]))))
                .hasMessageContaining("32 bytes");
    }

    @Test
    void propertiesNeverPrintSecrets() {
        assertThat(props("c2VjcmV0").toString()).doesNotContain("c2VjcmV0");
        assertThat(new SecurityProperties.Bootstrap("admin", "p4ssw0rd!").toString()).doesNotContain("p4ssw0rd!");
    }
}
