package com.cryptotrading.execution.domain;

public enum OrderRole {
    ENTRY('E'), EXIT('X'), TAKE_PROFIT('T'), STOP_LOSS('S');

    private final char code;

    OrderRole(char code) {
        this.code = code;
    }

    public char code() {
        return code;
    }

    public boolean isProtective() {
        return this == TAKE_PROFIT || this == STOP_LOSS;
    }
}
