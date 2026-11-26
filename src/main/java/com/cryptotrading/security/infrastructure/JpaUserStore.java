package com.cryptotrading.security.infrastructure;

import com.cryptotrading.security.application.UserStore;
import com.cryptotrading.security.domain.UserRole;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUserStore implements UserStore {

    private final AppUserRepository repository;

    JpaUserStore(AppUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredUser> findByUsername(String username) {
        return repository.findByUsername(username).map(e -> new StoredUser(e.id, e.username, e.passwordHash, e.role,
                e.totpSecretEnc, e.totpEnabled, e.totpLastStep, e.failedAttempts, e.lockedUntil));
    }

    @Override
    @Transactional
    public void create(UUID id, String username, String passwordHash, UserRole role, Instant now) {
        AppUserEntity e = new AppUserEntity();
        e.id = id;
        e.username = username;
        e.passwordHash = passwordHash;
        e.role = role;
        e.createdAt = now;
        e.updatedAt = now;
        repository.save(e);
    }

    @Override
    @Transactional
    public void recordLoginFailure(String username, int failedAttempts, Instant lockedUntil, Instant now) {
        repository.findByUsername(username).ifPresent(e -> {
            e.failedAttempts = failedAttempts;
            e.lockedUntil = lockedUntil;
            e.updatedAt = now;
        });
    }

    @Override
    @Transactional
    public void recordLoginSuccess(String username, Instant now) {
        repository.findByUsername(username).ifPresent(e -> {
            e.failedAttempts = 0;
            e.lockedUntil = null;
            e.updatedAt = now;
        });
    }

    @Override
    @Transactional
    public void setTotpSecret(String username, byte[] encryptedSecret, Instant now) {
        AppUserEntity e = repository.findByUsername(username).orElseThrow();
        e.totpSecretEnc = encryptedSecret;
        e.totpEnabled = false;
        e.totpLastStep = null;
        e.updatedAt = now;
    }

    @Override
    @Transactional
    public void enableTotp(String username, long step, Instant now) {
        AppUserEntity e = repository.findByUsername(username).orElseThrow();
        e.totpEnabled = true;
        e.totpLastStep = step;
        e.updatedAt = now;
    }

    @Override
    @Transactional
    public boolean consumeTotpStep(String username, long step, Instant now) {
        return repository.consumeStep(username, step, now) == 1;
    }
}
