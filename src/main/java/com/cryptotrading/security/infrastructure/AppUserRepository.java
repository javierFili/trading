package com.cryptotrading.security.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByUsername(String username);

    @Modifying
    @Query("""
            UPDATE AppUserEntity u SET u.totpLastStep = :step, u.updatedAt = :now, u.version = u.version + 1
            WHERE u.username = :username AND (u.totpLastStep IS NULL OR u.totpLastStep < :step)""")
    int consumeStep(@Param("username") String username, @Param("step") long step, @Param("now") Instant now);
}
