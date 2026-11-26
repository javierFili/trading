package com.cryptotrading.security.infrastructure;

import com.cryptotrading.security.application.SecurityProperties;
import com.cryptotrading.security.application.UserAccountService;
import com.cryptotrading.security.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Creates the first TRADER user from CTS_ADMIN_USERNAME / CTS_ADMIN_PASSWORD if it does not exist yet. */
@Component
class BootstrapAdmin {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserAccountService users;
    private final SecurityProperties properties;

    BootstrapAdmin(UserAccountService users, SecurityProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void createInitialUser() {
        SecurityProperties.Bootstrap b = properties.bootstrap();
        if (b == null || b.username() == null || b.username().isBlank() || users.exists(b.username())) {
            return;
        }
        users.createUser(b.username(), b.password(), UserRole.TRADER, "system:bootstrap");
        log.info("Initial TRADER user '{}' created. Enroll TOTP before approving proposals.", b.username());
    }
}
