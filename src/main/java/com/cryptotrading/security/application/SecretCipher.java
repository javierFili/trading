package com.cryptotrading.security.application;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** AES-256-GCM encryption of secrets at rest. Output = 12-byte IV ‖ ciphertext+tag. */
@Component
public class SecretCipher {

    private static final int IV_LENGTH = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(SecurityProperties properties) {
        byte[] raw = Base64.getDecoder().decode(properties.masterKey());
        if (raw.length != 32) {
            throw new IllegalStateException("CTS_MASTER_KEY must be 32 bytes encoded in Base64");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain);
            return ByteBuffer.allocate(IV_LENGTH + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(data, 0, IV_LENGTH)));
            return cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed (wrong master key or tampered data)", e);
        }
    }

    @Override
    public String toString() {
        return "SecretCipher[<redacted>]";
    }
}
