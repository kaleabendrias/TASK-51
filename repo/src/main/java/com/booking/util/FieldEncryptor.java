package com.booking.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public final class FieldEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int AES_KEY_LENGTH = 32; // 256 bits
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final byte[] PBKDF2_SALT = "BookingPortalFieldEncryptorSalt!".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec keySpec;

    private FieldEncryptor() {}

    public static void configure(String key) {
        if (key == null || key.length() < 32) {
            throw new IllegalArgumentException("Encryption key must be at least 32 characters for AES-256");
        }
        try {
            // Derive a proper 32-byte (256-bit) key via PBKDF2
            KeySpec spec = new PBEKeySpec(key.toCharArray(), PBKDF2_SALT, PBKDF2_ITERATIONS, AES_KEY_LENGTH * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] derived = factory.generateSecret(spec).getEncoded();
            keySpec = new SecretKeySpec(derived, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive encryption key via PBKDF2", e);
        }
    }

    private static SecretKeySpec getKeySpec() {
        if (keySpec == null) {
            throw new IllegalStateException(
                "Encryption key not configured. Set ENCRYPTION_KEY environment variable or app.encryption-key property.");
        }
        return keySpec;
    }

    public static boolean isConfigured() {
        return keySpec != null;
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKeySpec(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKeySpec(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
