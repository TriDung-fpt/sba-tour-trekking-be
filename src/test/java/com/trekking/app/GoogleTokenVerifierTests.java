package com.trekking.app;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.trekking.app.core.config.GoogleAuthConfig;
import com.trekking.app.core.config.GoogleAuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.account.service.google.GoogleIdentity;
import com.trekking.app.features.account.service.google.GoogleTokenVerifier;
import com.trekking.app.features.account.service.google.GoogleTokenVerifierImpl;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GoogleTokenVerifierTests {
    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CLIENT_ID = "trekking-client.apps.googleusercontent.com";
    private static final GoogleAuthProperties PROPERTIES = new GoogleAuthProperties(List.of(CLIENT_ID,
            "trekking-mobile.apps.googleusercontent.com"));
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final RSAKey KEY = key("google-test-key");
    private final GoogleTokenVerifier verifier = verifier(decoder());

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts.google.com", "https://accounts.google.com"})
    void acceptsGoogleSignatureAndIssuersAndNormalizesIdentity(String issuer) throws Exception {
        var identity = verifier.verify(sign(claims().issuer(issuer).claim("email", "  Person@Gmail.COM  ")
                .claim("name", "  Google Person  ")));

        assertThat(identity).isEqualTo(new GoogleIdentity("google-subject", "person@gmail.com", "Google Person", true));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidClaims")
    void rejectsMissingOrInvalidSecurityClaims(String description, Consumer<JWTClaimsSet.Builder> mutation) throws Exception {
        var claims = claims();
        mutation.accept(claims);

        assertInvalid(sign(claims));
    }

    static Stream<Arguments> invalidClaims() {
        return Stream.of(
                invalid("issuer", c -> c.issuer("https://attacker.test")),
                invalid("missing issuer", c -> c.issuer(null)),
                invalid("wrong audience", c -> c.audience("other-client")),
                invalid("missing audience", c -> c.audience((String) null)),
                invalid("wrong audience type", c -> c.claim("aud", 42)),
                invalid("missing expiry", c -> c.expirationTime(null)),
                invalid("expired token", c -> c.expirationTime(Date.from(NOW.minusSeconds(1)))),
                invalid("expiry boundary", c -> c.expirationTime(Date.from(NOW))),
                invalid("not yet valid", c -> c.notBeforeTime(Date.from(NOW.plusSeconds(30)))),
                invalid("missing subject", c -> c.subject(null)),
                invalid("array subject", c -> c.claim("sub", List.of("google-subject"))),
                invalid("blank subject", c -> c.subject("  ")),
                invalid("oversized subject", c -> c.subject("a".repeat(256))),
                invalid("missing verified email", c -> c.claim("email_verified", null)),
                invalid("unverified email", c -> c.claim("email_verified", false)),
                invalid("string verified flag", c -> c.claim("email_verified", "true")),
                invalid("missing email", c -> c.claim("email", null)),
                invalid("numeric email", c -> c.claim("email", 42)),
                invalid("malformed email", c -> c.claim("email", "bad@@gmail.com")),
                invalid("blank email", c -> c.claim("email", "  ")),
                invalid("oversized email", c -> c.claim("email", "a".repeat(245) + "@gmail.com")),
                invalid("wrong authorized party", c -> c.claim("azp", "other-client")),
                invalid("wrong authorized-party type", c -> c.claim("azp", 42)),
                invalid("multiple audiences without authorized party", c -> c.audience(List.of(CLIENT_ID, "other-client")))
        );
    }

    @Test
    void acceptsConfiguredAuthorizedPartyForMultipleAudiences() throws Exception {
        var token = sign(claims().audience(List.of(CLIENT_ID, "trekking-mobile.apps.googleusercontent.com"))
                .claim("azp", "trekking-mobile.apps.googleusercontent.com"));

        assertThat(verifier.verify(token).subject()).isEqualTo("google-subject");
    }

    @Test
    void rejectsMalformedUnsignedAndWrongAlgorithmTokens() throws Exception {
        assertInvalid("not-a-jwt");
        assertInvalid(new com.nimbusds.jwt.PlainJWT(claims().build()).serialize());
        assertInvalid(sign(claims(), KEY, JWSAlgorithm.RS512));
    }

    @Test
    void rejectsTokenSignedByAnotherKey() throws Exception {
        assertInvalid(sign(claims(), key("attacker-key"), JWSAlgorithm.RS256));
    }

    @Test
    void distinguishesEmailOwnershipForWorkspaceAndThirdPartyAccounts() throws Exception {
        assertThat(verifier.verify(sign(claims().claim("email", "person@workspace.test")
                .claim("hd", "workspace.test"))).emailAuthoritative()).isTrue();
        assertThat(verifier.verify(sign(claims().claim("email", "person@third-party.test"))).emailAuthoritative()).isFalse();
        assertThat(verifier.verify(sign(claims().claim("email", "person@third-party.test")
                .claim("hd", "  "))).emailAuthoritative()).isFalse();
    }

    @Test
    void usesEmailForMissingNameAndBoundsLongNamesWithoutSplittingUnicode() throws Exception {
        assertThat(verifier.verify(sign(claims().claim("name", "  "))).fullName()).isEqualTo("person");
        var longName = "a".repeat(199) + "\uD83D\uDE00" + "tail";
        assertThat(verifier.verify(sign(claims().claim("name", longName))).fullName()).isEqualTo("a".repeat(199));
    }

    @Test
    void disabledGoogleAuthenticationNeverCallsDecoder() {
        var decoder = mock(JwtDecoder.class);
        var disabled = new GoogleTokenVerifierImpl(new GoogleAuthProperties(null), decoder, CLOCK, VALIDATORS.getValidator());

        assertThatThrownBy(() -> disabled.verify("token")).isInstanceOfSatisfying(AuthException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(ex.getCode()).isEqualTo("GOOGLE_AUTH_UNAVAILABLE");
        });
        verifyNoInteractions(decoder);
        assertThat(new GoogleAuthProperties(java.util.Arrays.asList(null, "  ", CLIENT_ID, " " + CLIENT_ID)).clientIds())
                .containsExactly(CLIENT_ID);
    }

    @Test
    void googleConfigurationDoesNotAddAGlobalJwtDecoder() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var appDecoder = mock(JwtDecoder.class);
            context.registerBean(Clock.class, () -> CLOCK);
            context.registerBean(jakarta.validation.Validator.class, VALIDATORS::getValidator);
            context.registerBean("applicationDecoder", JwtDecoder.class, () -> appDecoder);
            context.register(GoogleAuthConfig.class);
            context.refresh();

            assertThat(context.getBeansOfType(JwtDecoder.class)).containsExactlyEntriesOf(Map.of("applicationDecoder", appDecoder));
            assertThat(context.getBean(GoogleTokenVerifier.class)).isNotNull();
            verifyNoInteractions(appDecoder);
        }
    }

    @Test
    void fetchesJwksForSignatureVerificationAndRejectsUnknownSigningKey() throws Exception {
        var transport = mock(RestOperations.class);
        when(transport.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(new JWKSet(KEY.toPublicJWK()).toString()));
        var decoder = jwksDecoder(transport);

        assertThat(verifier(decoder).verify(sign(claims())).subject()).isEqualTo("google-subject");
        assertInvalid(verifier(decoder), sign(claims(), key("unknown-key"), JWSAlgorithm.RS256));
        verify(transport, atLeastOnce()).exchange(any(RequestEntity.class), eq(String.class));
    }

    @Test
    void upstreamJwksFailureReturnsServiceUnavailable() throws Exception {
        var transport = mock(RestOperations.class);
        when(transport.exchange(any(RequestEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Unavailable", new IOException("Connection failed")));

        assertThatThrownBy(() -> verifier(jwksDecoder(transport)).verify(sign(claims())))
                .isInstanceOfSatisfying(AuthException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo("AUTH_UNAVAILABLE");
                });
    }

    @Test
    void malformedUpstreamJwksIsUnavailableAndDoesNotAuthenticate() throws Exception {
        var transport = mock(RestOperations.class);
        when(transport.exchange(any(RequestEntity.class), eq(String.class))).thenReturn(ResponseEntity.ok("invalid-jwks"));

        assertThatThrownBy(() -> verifier(jwksDecoder(transport)).verify(sign(claims())))
                .isInstanceOfSatisfying(AuthException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo("AUTH_UNAVAILABLE");
                });
    }

    @Test
    void wrapsDecoderFailuresWithoutLeakingProviderDetails() {
        var decoder = mock(JwtDecoder.class);
        when(decoder.decode("token")).thenThrow(new JwtException("Provider details"));

        assertInvalid(verifier(decoder), "token");
        doThrow(new JwtException("Provider details", new RemoteKeySourceException("upstream", null)))
                .when(decoder).decode("token");
        assertThatThrownBy(() -> verifier(decoder).verify("token")).isInstanceOfSatisfying(AuthException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("AUTH_UNAVAILABLE"));
    }

    private static Arguments invalid(String description, Consumer<JWTClaimsSet.Builder> mutation) {
        return Arguments.of(description, mutation);
    }

    private static RSAKey key(String id) {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("https://accounts.google.com").subject("google-subject")
                .audience(CLIENT_ID).issueTime(Date.from(NOW.minusSeconds(60)))
                .expirationTime(Date.from(NOW.plusSeconds(300))).claim("email", "person@gmail.com")
                .claim("email_verified", true).claim("name", "Google Person");
    }

    private static String sign(JWTClaimsSet.Builder claims) throws Exception {
        return sign(claims, KEY, JWSAlgorithm.RS256);
    }

    private static String sign(JWTClaimsSet.Builder claims, RSAKey key, JWSAlgorithm algorithm) throws Exception {
        var token = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(key.getKeyID()).build(), claims.build());
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }

    private static NimbusJwtDecoder decoder() {
        try {
            return withClock(NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static NimbusJwtDecoder jwksDecoder(RestOperations transport) {
        return withClock(NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(transport).build());
    }

    private static NimbusJwtDecoder withClock(NimbusJwtDecoder decoder) {
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(CLOCK);
        decoder.setJwtValidator(timestamps);
        return decoder;
    }

    private static GoogleTokenVerifier verifier(JwtDecoder decoder) {
        return new GoogleTokenVerifierImpl(PROPERTIES, decoder, CLOCK, VALIDATORS.getValidator());
    }

    private void assertInvalid(String token) {
        assertInvalid(verifier, token);
    }

    private static void assertInvalid(GoogleTokenVerifier verifier, String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOfSatisfying(AuthException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(ex.getCode()).isEqualTo("INVALID_GOOGLE_TOKEN");
            assertThat(ex.getMessage()).doesNotContain("Provider details");
        });
    }
}
