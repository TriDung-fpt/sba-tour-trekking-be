package com.trekking.app.features.auth.service.impl;

import com.trekking.app.features.auth.model.GoogleIdentity;
import com.trekking.app.features.auth.service.GoogleTokenVerifier;

import com.nimbusds.jose.RemoteKeySourceException;
import com.trekking.app.core.config.GoogleAuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.auth.validation.AuthInputNormalizer;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public class GoogleTokenVerifierImpl implements GoogleTokenVerifier {
    private static final List<String> GOOGLE_ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");
    private final GoogleAuthProperties properties;
    private final JwtDecoder decoder;
    private final Clock clock;
    private final Validator validator;

    public GoogleTokenVerifierImpl(GoogleAuthProperties properties, JwtDecoder decoder, Clock clock, Validator validator) {
        this.properties = properties;
        this.decoder = decoder;
        this.clock = clock;
        this.validator = validator;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (!properties.enabled()) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_AUTH_UNAVAILABLE",
                    "Google sign-in is not configured");
        }
        if (idToken == null || idToken.isBlank()) throw invalidToken();

        final Jwt token;
        try {
            token = decoder.decode(idToken);
        } catch (JwtException ex) {
            if (hasRemoteKeyFailure(ex)) throw AuthException.unavailable();
            throw invalidToken();
        }

        Object expiration = token.getClaims().get("exp");
        Object notBefore = token.getClaims().get("nbf");
        var now = clock.instant();
        if (!"RS256".equals(token.getHeaders().get("alg"))
                || !(token.getClaims().get("iss") instanceof String issuer) || !GOOGLE_ISSUERS.contains(issuer)
                || !(expiration instanceof Instant expiresAt) || !expiresAt.isAfter(now)
                || notBefore != null && (!(notBefore instanceof Instant startsAt) || startsAt.isAfter(now))
                || !validAudience(token)
                || !(token.getClaims().get("sub") instanceof String subject) || subject.isBlank() || subject.length() > 255
                || !Boolean.TRUE.equals(token.getClaims().get("email_verified"))
                || !(token.getClaims().get("email") instanceof String rawEmail)) {
            throw invalidToken();
        }

        String email = AuthInputNormalizer.email(rawEmail);
        if (!validator.validate(new EmailClaim(email)).isEmpty()) throw invalidToken();
        Object hostedDomain = token.getClaims().get("hd");
        boolean authoritative = email.endsWith("@gmail.com")
                || hostedDomain instanceof String domain && !domain.isBlank();
        return new GoogleIdentity(subject, email, fullName(token, email), authoritative);
    }

    private boolean validAudience(Jwt token) {
        Object audienceClaim = token.getClaims().get("aud");
        if (!(audienceClaim instanceof List<?> audiences) || audiences.isEmpty()
                || audiences.stream().anyMatch(audience -> !(audience instanceof String))
                || audiences.stream().noneMatch(properties.clientIds()::contains)) return false;
        Object authorizedParty = token.getClaims().get("azp");
        if (authorizedParty != null) {
            return authorizedParty instanceof String clientId && properties.clientIds().contains(clientId);
        }
        return audiences.size() == 1;
    }

    private String fullName(Jwt token, String email) {
        Object nameClaim = token.getClaims().get("name");
        String name = nameClaim instanceof String value ? AuthInputNormalizer.fullName(value) : null;
        if (name == null || name.isBlank()) name = email.substring(0, email.indexOf('@'));
        if (name.isBlank()) name = "Google user";
        if (name.length() <= 200) return name;
        int end = Character.isHighSurrogate(name.charAt(199)) && Character.isLowSurrogate(name.charAt(200)) ? 199 : 200;
        return name.substring(0, end);
    }

    private boolean hasRemoteKeyFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RemoteKeySourceException) return true;
        }
        return false;
    }

    private AuthException invalidToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google ID token is invalid or expired");
    }

    private record EmailClaim(@NotBlank @Email @Size(max = 254) String email) {}
}
