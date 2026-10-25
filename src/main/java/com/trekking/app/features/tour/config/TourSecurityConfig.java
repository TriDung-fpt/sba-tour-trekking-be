package com.trekking.app.features.tour.config;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.security.SecurityErrorHandler;
import com.trekking.app.core.security.SessionJwtAuthenticationConverter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class TourSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain tourSecurityFilterChain(HttpSecurity http,
                                                AuthProperties properties,
                                                SessionJwtAuthenticationConverter converter,
                                                SecurityErrorHandler errors) throws Exception {
        http.securityMatcher("/api/v1/tours", "/api/v1/tours/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/tours/equipment-catalog",
                                "/api/v1/tours/rental-packages").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/tours",
                                "/api/v1/tours/*",
                                "/api/v1/tours/*/schedules",
                                "/api/v1/tours/*/schedules/*",
                                "/api/v1/tours/*/activities",
                                "/api/v1/tours/*/required-items",
                                "/api/v1/tours/*/equipment").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors));
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
