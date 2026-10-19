package com.trekking.app;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.security.JwtService;
import com.trekking.app.core.security.JwtServiceImpl;
import com.trekking.app.features.account.dto.response.AccountResponse;
import com.trekking.app.features.auth.model.AuthSession;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthProperties PROPERTIES = new AuthProperties(
            "test-jwt-key-012345678901234567890123456789", "test-otp-key-012345678901234567890123456789",
            "https://trekking.test", "trekking-test", Duration.ofMinutes(15), Duration.ofDays(7),
            true, List.of(), "test@example.invalid", "test:");
    private final AccountResponse account = new AccountResponse(42L, "test@example.invalid", "Test", null,
            List.of("CUSTOMER", "GUIDE"));
    private final JwtService service = new JwtServiceImpl(new NimbusJwtEncoder(new ImmutableSecret<>(
            PROPERTIES.jwtSecret().getBytes(StandardCharsets.UTF_8))), PROPERTIES, CLOCK);

    @Test
    void issuesSignedTokenWithExistingSessionAndAccountClaims() {
        var token = decode(service.issue(new AuthSession("session-id", 42L, "fingerprint", NOW.plus(Duration.ofDays(1))), account));

        assertThat(token.getHeaders()).containsEntry("alg", "HS256");
        assertThat(token.getClaimAsString("iss")).isEqualTo(PROPERTIES.issuer());
        assertThat(token.getAudience()).containsExactly(PROPERTIES.audience());
        assertThat(token.getSubject()).isEqualTo("42");
        assertThat(token.getIssuedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(PROPERTIES.accessTtl()));
        assertThat(token.getClaimAsString("sid")).isEqualTo("session-id");
        assertThat(token.getClaimAsStringList("roles")).containsExactly("CUSTOMER", "GUIDE");
    }

    @Test
    void accessTokenCannotOutliveItsSession() {
        var sessionExpires = NOW.plusSeconds(90);

        var token = decode(service.issue(new AuthSession("session-id", 42L, "fingerprint", sessionExpires), account));

        assertThat(token.getExpiresAt()).isEqualTo(sessionExpires);
    }

    private Jwt decode(String value) {
        var key = new SecretKeySpec(PROPERTIES.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(CLOCK);
        decoder.setJwtValidator(timestamps);
        return decoder.decode(value);
    }
}
