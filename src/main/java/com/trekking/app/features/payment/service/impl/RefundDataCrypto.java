package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.exception.AuthException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for refund bank details.
 * Stored format: {@code v1:base64(nonce[12] | ciphertext | tag[16])}. The version prefix allows key rotation;
 * the associated data binds a ciphertext to its column so values cannot be swapped between fields.
 */
@Component
public class RefundDataCrypto {
    /** Associated-data labels for the payer account captured from the payment provider. */
    public static final String PAYER_ACCOUNT_FIELD = "customer_payments.payer_account";
    public static final String PAYER_NAME_FIELD = "customer_payments.payer_name";
    private static final String VERSION = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public RefundDataCrypto(@Value("${app.payment.refund-encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            key = null;
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("REFUND_DATA_ENCRYPTION_KEY must be Base64", ex);
        }
        if (raw.length != 32) throw new IllegalArgumentException("REFUND_DATA_ENCRYPTION_KEY must decode to 32 bytes");
        key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext, String field) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
            return VERSION + Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt refund data", ex);
        }
    }

    public String decrypt(String stored, String field) {
        if (stored == null) return null;
        requireKey();
        if (!stored.startsWith(VERSION)) throw new IllegalStateException("Unsupported refund ciphertext version");
        try {
            byte[] input = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, input, 0, NONCE_BYTES));
            cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(input, NONCE_BYTES, input.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt refund data", ex);
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    private void requireKey() {
        if (key == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "REFUND_ENCRYPTION_UNAVAILABLE",
                    "Refund data encryption key is not configured");
        }
    }
}
