package com.cryptotrading.security.application;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.security.domain.Base32;
import com.cryptotrading.security.domain.Totp;
import com.cryptotrading.security.domain.UserRole;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

@Service
public class UserAccountService implements SecondFactorVerifier {

    public static final int MIN_PASSWORD_LENGTH = 12;
    private static final String ISSUER = "CryptoTradingSystem";
    /** Hash used to keep timing similar when the user does not exist. */
    private final String dummyHash;

    public record TotpEnrollment(String secret, String otpauthUri) {
        @Override
        public String toString() {
            return "TotpEnrollment[<redacted>]";
        }
    }

    private final UserStore store;
    private final PasswordEncoder encoder;
    private final SecretCipher cipher;
    private final SecurityProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UserAccountService(UserStore store, PasswordEncoder encoder, SecretCipher cipher, SecurityProperties properties,
                              ApplicationEventPublisher events, Clock clock) {
        this.store = store;
        this.encoder = encoder;
        this.cipher = cipher;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public UserAccount createUser(String username, String password, UserRole role, String actor) {
        if (username == null || !username.matches("^[a-zA-Z0-9._-]{3,50}$")) {
            throw new IllegalArgumentException("Invalid username");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (store.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }
        UUID id = UUID.randomUUID();
        store.create(id, username, encoder.encode(password), role, clock.instant());
        events.publishEvent(new AuditEvent(actor, "USER_CREATED", "user", id.toString(), Map.of("username", username, "role", role.name())));
        return new UserAccount(id, username, role, false, null);
    }

    public boolean exists(String username) {
        return store.findByUsername(username).isPresent();
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public UserAccount authenticate(String username, String password, String clientIp) {
        Instant now = clock.instant();
        UserStore.StoredUser user = store.findByUsername(username == null ? "" : username).orElse(null);
        if (user == null) {
            encoder.matches(password == null ? "" : password, dummyHash);
            throw new AuthenticationFailedException();
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            events.publishEvent(new AuditEvent(username, "LOGIN_REJECTED_LOCKED", "user", user.id().toString(), Map.of("ip", String.valueOf(clientIp))));
            throw new AuthenticationFailedException();
        }
        if (password == null || !encoder.matches(password, user.passwordHash())) {
            int failures = user.failedAttempts() + 1;
            Instant lockedUntil = failures >= properties.maxFailedLogins() ? now.plus(properties.lockDuration()) : null;
            store.recordLoginFailure(username, lockedUntil != null ? 0 : failures, lockedUntil, now);
            events.publishEvent(new AuditEvent(username, lockedUntil != null ? "USER_LOCKED" : "LOGIN_FAILED", "user",
                    user.id().toString(), Map.of("ip", String.valueOf(clientIp))));
            throw new AuthenticationFailedException();
        }
        store.recordLoginSuccess(username, now);
        events.publishEvent(new AuditEvent(username, "LOGIN_SUCCEEDED", "user", user.id().toString(), Map.of("ip", String.valueOf(clientIp))));
        return toAccount(user);
    }

    public UserAccount account(String username) {
        return store.findByUsername(username).map(UserAccountService::toAccount)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
    }

    /** Starts (or restarts, while not yet confirmed) TOTP enrollment. Returns the secret to the user only. */
    @Transactional
    public TotpEnrollment beginTotpEnrollment(String username) {
        UserStore.StoredUser user = store.findByUsername(username).orElseThrow();
        if (user.totpEnabled()) {
            throw new IllegalStateException("TOTP already enabled");
        }
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        store.setTotpSecret(username, cipher.encrypt(secret), clock.instant());
        String encoded = Base32.encode(secret);
        String label = URLEncoder.encode(ISSUER + ":" + username, StandardCharsets.UTF_8).replace("+", "%20");
        String uri = "otpauth://totp/" + label + "?secret=" + encoded + "&issuer=" + ISSUER + "&algorithm=SHA1&digits=6&period=30";
        events.publishEvent(new AuditEvent(username, "TOTP_ENROLLMENT_STARTED", "user", user.id().toString(), Map.of()));
        return new TotpEnrollment(encoded, uri);
    }

    @Transactional
    public void confirmTotpEnrollment(String username, String code) {
        UserStore.StoredUser user = store.findByUsername(username).orElseThrow();
        if (user.totpEnabled() || user.totpSecretEncrypted() == null) {
            throw new IllegalStateException("No pending TOTP enrollment");
        }
        OptionalLong step = Totp.verify(cipher.decrypt(user.totpSecretEncrypted()), code, clock.instant());
        if (step.isEmpty()) {
            throw new IllegalArgumentException("Invalid code");
        }
        store.enableTotp(username, step.getAsLong(), clock.instant());
        events.publishEvent(new AuditEvent(username, "TOTP_ENABLED", "user", user.id().toString(), Map.of()));
    }

    /**
     * Runs in its own transaction so a consumed code stays consumed even if the guarded action then fails
     * (e.g. version conflict): a code that was seen once can never be replayed.
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean verify(String username, String code) {
        UserStore.StoredUser user = store.findByUsername(username).orElse(null);
        if (user == null || !user.totpEnabled() || user.totpSecretEncrypted() == null) {
            return false;
        }
        OptionalLong step = Totp.verify(cipher.decrypt(user.totpSecretEncrypted()), code, clock.instant());
        return step.isPresent() && store.consumeTotpStep(username, step.getAsLong(), clock.instant());
    }

    private static UserAccount toAccount(UserStore.StoredUser u) {
        return new UserAccount(u.id(), u.username(), u.role(), u.totpEnabled(), u.lockedUntil());
    }
}
