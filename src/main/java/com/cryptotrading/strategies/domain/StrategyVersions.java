package com.cryptotrading.strategies.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Version = logic version + short hash of the parameters, so any parameter change yields a new version. */
public final class StrategyVersions {

    private StrategyVersions() {
    }

    public static String of(String logicVersion, Map<String, String> parameters) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(new TreeMap<>(parameters).toString().getBytes(StandardCharsets.UTF_8));
            return logicVersion + "+" + HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
