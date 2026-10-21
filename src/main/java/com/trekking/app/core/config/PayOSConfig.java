package com.trekking.app.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;

@Configuration
@EnableConfigurationProperties(PayOSProperties.class)
public class PayOSConfig {
    @Bean
    PayOS payOS(PayOSProperties properties) {
        if (!properties.configured()) return null;
        return new PayOS(ClientOptions.builder().clientId(properties.clientId()).apiKey(properties.apiKey())
                .checksumKey(properties.checksumKey()).baseURL(properties.baseUrl()).build());
    }
}
