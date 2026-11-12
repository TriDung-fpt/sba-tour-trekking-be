package com.trekking.app.features.policy.config;

import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.public-policies")
public record PublicPolicyProperties(
        @DefaultValue("BOOKING_GUIDE") String bookingGuideCode,
        @DefaultValue("REFUND_POLICY") String refundPolicyCode,
        @DefaultValue("WEBSITE_TERMS") String websiteTermsCode,
        @DefaultValue("PRIVACY_POLICY") String privacyPolicyCode,
        @DefaultValue("LIABILITY_WAIVER") String liabilityWaiverCode) {

    public Optional<String> policyCode(String documentCode) {
        String code = switch (documentCode.toUpperCase(Locale.ROOT)) {
            case "BOOKING_GUIDE" -> bookingGuideCode;
            case "REFUND_POLICY" -> refundPolicyCode;
            case "WEBSITE_TERMS" -> websiteTermsCode;
            case "PRIVACY_POLICY" -> privacyPolicyCode;
            case "LIABILITY_WAIVER" -> liabilityWaiverCode;
            default -> null;
        };
        return Optional.ofNullable(code).map(String::trim).filter(value -> !value.isEmpty());
    }
}
