package com.cryptotrading.exchange.domain;

/** Handle of a live stream subscription. Implementations reconnect transparently with backoff. */
public interface StreamSubscription extends AutoCloseable {

    boolean isConnected();

    @Override
    void close();
}
