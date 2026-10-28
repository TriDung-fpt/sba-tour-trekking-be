package com.trekking.app.features.payment.service.impl;

import com.trekking.app.features.payment.config.PayOsProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PayOsGateway {
    private final PayOsProperties properties;
    private final RestClient client;

    public PayOsGateway(PayOsProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.getApiBaseUrl()).build();
    }

    public LinkResult createLink(long orderCode, long amountVnd, String description, long expiresAtEpochSeconds) {
        properties.requireConfigured();
        String signature = sign("amount=" + amountVnd
                + "&cancelUrl=" + properties.getCancelUrl()
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + properties.getReturnUrl());
        var body = new CreateLinkBody(orderCode, amountVnd, description,
                properties.getCancelUrl(), properties.getReturnUrl(), expiresAtEpochSeconds, signature);
        var response = client.post().uri("/v2/payment-requests")
                .header("x-client-id", properties.getClientId())
                .header("x-api-key", properties.getApiKey())
                .body(body).retrieve().body(PayOsCreateResponse.class);
        if (response == null || !"00".equals(response.code()) || response.data() == null
                || response.data().checkoutUrl() == null || response.data().checkoutUrl().isBlank()) {
            throw new IllegalStateException("PayOS did not return a checkout link");
        }
        return new LinkResult(response.data().checkoutUrl());
    }

    public boolean verifyWebhook(String signature, String canonicalData) {
        if (signature == null || signature.isBlank()) return false;
        String expected = sign(canonicalData);
        return java.security.MessageDigest.isEqual(expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public String webhookSignature(String canonicalData) {
        properties.requireConfigured();
        return sign(canonicalData);
    }

    private String sign(String data) {
        properties.requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getChecksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign PayOS request", ex);
        }
    }

    public record LinkResult(String checkoutUrl) {}
    private record CreateLinkBody(long orderCode, long amount, String description, String cancelUrl,
                                 String returnUrl, long expiredAt, String signature) {}
    private record PayOsCreateResponse(String code, String desc, PayOsLinkData data) {}
    private record PayOsLinkData(String checkoutUrl) {}
}
