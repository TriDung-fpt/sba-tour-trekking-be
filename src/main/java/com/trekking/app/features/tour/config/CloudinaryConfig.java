package com.trekking.app.features.tour.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryConfig {
    @Bean("tourCloudinary")
    Cloudinary tourCloudinary(CloudinaryProperties properties) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.cloudName(), "api_key", properties.apiKey(),
                "api_secret", properties.apiSecret(), "secure", true, "timeout", 30));
    }
}
