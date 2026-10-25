package com.trekking.app.features.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("app.storage.cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret) {
    public boolean configured() {
        return StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret);
    }

    @Override
    public String toString() {
        return "CloudinaryProperties[credentials hidden]";
    }
}
