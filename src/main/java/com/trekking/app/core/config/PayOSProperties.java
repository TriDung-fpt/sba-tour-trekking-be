package com.trekking.app.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.payos")
public record PayOSProperties(String clientId, String apiKey, String checksumKey, String baseUrl,
                              String returnUrl, String cancelUrl) {
    public boolean configured() {
        return present(clientId) && present(apiKey) && present(checksumKey)
                && present(returnUrl) && present(cancelUrl);
    }
    private boolean present(String value) { return value != null && !value.isBlank(); }
}
