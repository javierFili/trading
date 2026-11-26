package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.StreamSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket connection that reconnects with exponential backoff after errors/closures and proactively before
 * the exchange's 24 h connection limit. Pings are answered automatically by the JDK client.
 * Messages may be lost while disconnected: consumers must back-fill via REST.
 */
final class ReconnectingWebSocket implements StreamSubscription {

    private static final Logger log = LoggerFactory.getLogger(ReconnectingWebSocket.class);
    static final Duration MAX_CONNECTION_AGE = Duration.ofHours(23);

    private final HttpClient client;
    private final URI uri;
    private final Consumer<String> onMessage;
    private final Runnable onReconnected;
    private final Backoff backoff;
    private final ScheduledExecutorService scheduler;
    private volatile WebSocket socket;
    private volatile boolean closed;
    private volatile boolean connected;
    private int attempt;
    private ScheduledFuture<?> rotation;

    ReconnectingWebSocket(HttpClient client, URI uri, Consumer<String> onMessage, Runnable onReconnected,
                          Backoff backoff, ScheduledExecutorService scheduler) {
        this.client = client;
        this.uri = uri;
        this.onMessage = onMessage;
        this.onReconnected = onReconnected;
        this.backoff = backoff;
        this.scheduler = scheduler;
    }

    void start() {
        connect();
    }

    private synchronized void connect() {
        if (closed) {
            return;
        }
        client.newWebSocketBuilder().buildAsync(uri, new Listener()).whenComplete((ws, error) -> {
            if (error != null) {
                log.warn("WebSocket connect to {} failed: {}", uri, error.getMessage());
                scheduleReconnect();
                return;
            }
            boolean wasReconnect;
            synchronized (this) {
                socket = ws;
                connected = true;
                wasReconnect = attempt > 0;
                attempt = 0;
                if (rotation != null) {
                    rotation.cancel(false);
                }
                rotation = scheduler.schedule(this::rotate, MAX_CONNECTION_AGE.toMinutes(), TimeUnit.MINUTES);
            }
            log.info("WebSocket connected: {}", uri);
            if (wasReconnect) {
                onReconnected.run();
            }
        });
    }

    private void rotate() {
        WebSocket ws = socket;
        if (ws != null) {
            ws.abort();
        }
        connected = false;
        scheduleReconnect();
    }

    private synchronized void scheduleReconnect() {
        connected = false;
        if (closed) {
            return;
        }
        Duration delay = backoff.delay(attempt++);
        log.info("Reconnecting to {} in {} ms (attempt {})", uri, delay.toMillis(), attempt);
        scheduler.schedule(this::connect, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public synchronized void close() {
        closed = true;
        connected = false;
        if (rotation != null) {
            rotation.cancel(false);
        }
        WebSocket ws = socket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                try {
                    onMessage.accept(message);
                } catch (RuntimeException e) {
                    log.warn("Error handling WebSocket message: {}", e.getMessage());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("WebSocket closed ({}): {}", statusCode, reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("WebSocket error: {}", error.getMessage());
            scheduleReconnect();
        }
    }
}
