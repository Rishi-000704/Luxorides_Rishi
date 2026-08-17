package com.core.services.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SecretCryptoService {

    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec keySpec;

    public SecretCryptoService(@Value("${fleetovo.secrets.master-key}") String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("fleetovo.secrets.master-key is required");
        }

        this.keySpec = new SecretKeySpec(sha256(masterKey), "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] output = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);

            return PREFIX + Base64.getEncoder().encodeToString(output);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt secret", ex);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }

        if (!encryptedText.startsWith(PREFIX)) {
            return encryptedText;
        }

        try {
            byte[] input = Base64.getDecoder().decode(encryptedText.substring(PREFIX.length()));

            byte[] iv = Arrays.copyOfRange(input, 0, IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(input, IV_LENGTH_BYTES, input.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt secret", ex);
        }
    }

    public boolean hasSecret(String encryptedText) {
        return encryptedText != null && !encryptedText.isBlank();
    }

    public String masked(String encryptedText) {
        if (!hasSecret(encryptedText)) {
            return null;
        }

        try {
            String plainText = decrypt(encryptedText);

            if (plainText == null || plainText.isBlank()) {
                return null;
            }

            return maskPlainText(plainText);
        } catch (Exception ex) {
            return "********";
        }
    }

    private String maskPlainText(String value) {
        if (value.length() <= 4) {
            return "****";
        }

        if (value.length() <= 8) {
            return value.charAt(0) + "****" + value.charAt(value.length() - 1);
        }

        return value.substring(0, 4) + "********" + value.substring(value.length() - 4);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize encryption key", ex);
        }
    }
}
