package com.cryptotrading.security.infrastructure;

import com.cryptotrading.security.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
class AppUserEntity {

    @Id
    UUID id;
    String username;
    @Column(name = "password_hash") String passwordHash;
    @Enumerated(EnumType.STRING) UserRole role;
    @Column(name = "totp_secret_enc") byte[] totpSecretEnc;
    @Column(name = "totp_enabled") boolean totpEnabled;
    @Column(name = "totp_last_step") Long totpLastStep;
    @Column(name = "failed_attempts") int failedAttempts;
    @Column(name = "locked_until") Instant lockedUntil;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;
}
