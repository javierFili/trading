package com.cryptotrading.security.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code security.*}. Secrets come from environment variables:
 * {@code CTS_JWT_SECRET} and {@code CTS_MASTER_KEY} (Base64, ≥ 32 bytes), {@code CTS_ADMIN_USERNAME/PASSWORD}.
 */
@Validated
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        @NotBlank String jwtSecret,
        @DefaultValue("30m") Duration jwtTtl,
        @NotBlank String masterKey,
        @Min(1) @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockDuration,
        @DefaultValue Bootstrap bootstrap) {

    public record Bootstrap(String username, String password) {
        @Override
        public String toString() {
            return "Bootstrap[username=" + username + ", password=<redacted>]";
        }
    }

    @Override
    public String toString() {
        return "SecurityProperties[jwtSecret=<redacted>, masterKey=<redacted>, jwtTtl=" + jwtTtl + "]";
    }
}
