package com.trekking.app.features.payment.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RefundDataCrypto {
    private static final int NONCE_BYTES = 12;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public RefundDataCrypto(@Value("${app.payment.refund-encryption-key:}") String encodedKey) {
        try {
            key = encodedKey == null || encodedKey.isBlank() ? new byte[0] : Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("REFUND_DATA_ENCRYPTION_KEY must be Base64", ex);
        }
        if (key.length != 0 && key.length != 32) {
            throw new IllegalArgumentException("REFUND_DATA_ENCRYPTION_KEY must decode to 32 bytes");
        }
    }

    public String encrypt(String plaintext) {
        if (key.length != 32) throw new IllegalStateException("Refund recipient encryption key is not configured");
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt refund recipient data", ex);
        }
    }

    public String decrypt(String ciphertext) {
        if (key.length != 32) throw new IllegalStateException("Refund recipient encryption key is not configured");
        try {
            byte[] input = Base64.getDecoder().decode(ciphertext);
            byte[] nonce = java.util.Arrays.copyOfRange(input, 0, NONCE_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(input, NONCE_BYTES, input.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt refund recipient data", ex);
        }
    }
}
