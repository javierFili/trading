package com.cryptotrading.notifications.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/** {@code /ws}: authenticated by the security filter chain (JWT as {@code access_token} query parameter). */
@Configuration
@EnableWebSocket
class NotificationConfiguration implements WebSocketConfigurer {

    private final NotificationHub hub;
    private final List<String> allowedOrigins;

    NotificationConfiguration(NotificationHub hub, @Value("${notifications.allowed-origins:}") List<String> allowedOrigins) {
        this.hub = hub;
        this.allowedOrigins = allowedOrigins.stream().filter(o -> !o.isBlank()).toList();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(hub, "/ws");
        if (!allowedOrigins.isEmpty()) {
            registration.setAllowedOrigins(allowedOrigins.toArray(String[]::new));
        }
    }
}
