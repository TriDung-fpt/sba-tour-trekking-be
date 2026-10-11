package com.trekking.app.core.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth.google")
public record GoogleAuthProperties(List<String> clientIds) {
    public GoogleAuthProperties {
        clientIds = clientIds == null ? List.of() : clientIds.stream()
                .filter(id -> id != null && !id.isBlank()).map(String::trim).distinct().toList();
    }

    public boolean enabled() {
        return !clientIds.isEmpty();
    }
}
