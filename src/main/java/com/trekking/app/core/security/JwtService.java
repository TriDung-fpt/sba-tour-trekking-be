package com.trekking.app.core.security;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.features.account.dto.response.AccountResponse;
import com.trekking.app.features.auth.model.AuthSession;
import java.time.Clock;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(AuthSession session, AccountResponse account) {
        var now = clock.instant();
        var expires = now.plus(properties.accessTtl());
        if (expires.isAfter(session.expiresAt())) expires = session.expiresAt();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(java.util.List.of(properties.audience()))
                .subject(account.id().toString()).issuedAt(now).expiresAt(expires)
                .claim("sid", session.id()).claim("roles", account.roles()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
