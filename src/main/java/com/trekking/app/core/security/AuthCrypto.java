package com.trekking.app.core.security;

import com.trekking.app.core.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AuthCrypto {
    private final SecureRandom random = new SecureRandom();
    private final byte[] key;

    public AuthCrypto(AuthProperties properties) {
        key = properties.otpSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String otp() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }

    public String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }

    public String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }

    public String passwordFingerprint(String passwordHash) { return hmac("password:" + passwordHash); }

    public String sessionFingerprint(String passwordHash, long sessionVersion) {
        // Version zero keeps sessions issued before migration 002 compatible.
        return sessionVersion == 0 ? passwordFingerprint(passwordHash)
                : hmac("session:" + sessionVersion + ":password:" + passwordHash);
    }

    public boolean same(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
