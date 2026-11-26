package com.cryptotrading.security.application;

import com.cryptotrading.security.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/** User as seen by the application layer (no password hash, no secrets). */
public record UserAccount(UUID id, String username, UserRole role, boolean totpEnabled, Instant lockedUntil) {
}
