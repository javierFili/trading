package com.cryptotrading.security.application;

/** Deliberately generic: never reveals whether the user exists, is locked or the password is wrong. */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Invalid credentials");
    }
}
