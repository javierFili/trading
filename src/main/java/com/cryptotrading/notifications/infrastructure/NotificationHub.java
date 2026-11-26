package com.cryptotrading.notifications.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Broadcasts JSON notifications to authenticated WebSocket clients. Server → client only. */
@Component
public class NotificationHub extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationHub.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JsonMapper json;
    private final Clock clock;

    NotificationHub(JsonMapper json, Clock clock) {
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (session.getPrincipal() == null) {
            closeQuietly(session);
            return;
        }
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 256 * 1024));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Read-only channel: client messages are ignored. Actions go through the authenticated REST API.
    }

    public void broadcast(String type, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("at", clock.instant().toString());
        envelope.put("payload", payload);
        TextMessage message = new TextMessage(json.writeValueAsString(envelope));
        sessions.values().forEach(s -> {
            try {
                if (s.isOpen()) {
                    s.sendMessage(message);
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("Dropping WebSocket session {}: {}", s.getId(), e.getMessage());
                sessions.remove(s.getId());
            }
        });
    }

    public int connectedClients() {
        return sessions.size();
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
