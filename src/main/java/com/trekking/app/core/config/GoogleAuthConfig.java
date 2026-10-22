package com.trekking.app.core.config;

import com.trekking.app.features.auth.service.GoogleTokenVerifier;
import com.trekking.app.features.auth.service.impl.GoogleTokenVerifierImpl;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(GoogleAuthProperties.class)
public class GoogleAuthConfig {
    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    @Bean
    GoogleTokenVerifier googleTokenVerifier(GoogleAuthProperties properties, Clock clock, Validator validator) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(5));
        requests.setReadTimeout(Duration.ofSeconds(5));
        // Kept private to this verifier so Google tokens cannot authenticate the application's resource server.
        var decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(requests)).build();
        return new GoogleTokenVerifierImpl(properties, decoder, clock, validator);
    }
}
