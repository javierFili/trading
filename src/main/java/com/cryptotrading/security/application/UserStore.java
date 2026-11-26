package com.cryptotrading.security.application;

import com.cryptotrading.security.domain.UserRole;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for users. */
public interface UserStore {

    record StoredUser(UUID id, String username, String passwordHash, UserRole role, byte[] totpSecretEncrypted,
                      boolean totpEnabled, Long totpLastStep, int failedAttempts, Instant lockedUntil) {
    }

    Optional<StoredUser> findByUsername(String username);

    void create(UUID id, String username, String passwordHash, UserRole role, Instant now);

    void recordLoginFailure(String username, int failedAttempts, Instant lockedUntil, Instant now);

    void recordLoginSuccess(String username, Instant now);

    void setTotpSecret(String username, byte[] encryptedSecret, Instant now);

    void enableTotp(String username, long step, Instant now);

    /** Atomically stores the used step only if it is newer than the last used one. */
    boolean consumeTotpStep(String username, long step, Instant now);
}
