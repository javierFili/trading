package com.cryptotrading.shared.domain;

/** A request that violates a business rule. {@link Kind} drives the HTTP status in the API layer. */
public class BusinessRuleException extends RuntimeException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, GONE, FORBIDDEN }

    private final Kind kind;
    private final String code;

    public BusinessRuleException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}
