package com.cryptotrading.security.application;

/** Verifies a one-time code of an enrolled user. Each code can be used only once (replay protection). */
public interface SecondFactorVerifier {

    boolean verify(String username, String code);
}
