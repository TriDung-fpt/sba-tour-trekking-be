package com.trekking.app.features.payment.service.impl;

import com.trekking.app.features.payment.config.PayOsProperties;
import com.trekking.app.features.payment.enums.PaymentProvider;
import com.trekking.app.features.payment.service.PaymentGateway;
import com.trekking.app.core.exception.AuthException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class PayOsGateway implements PaymentGateway {
    private final PayOsProperties properties;
    private final RestClient client;

    public PayOsGateway(PayOsProperties properties) {
        this.properties = properties;
        this.client = RestClient.builder().baseUrl(properties.getApiBaseUrl()).build();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.PAYOS;
    }

    @Override
    public String createLink(String merchantReference, long amountVnd, String description, OffsetDateTime expiresAt) {
        properties.requireConfigured();
        long orderCode = Long.parseLong(merchantReference);
        long expiresAtEpochSeconds = expiresAt.toEpochSecond();
        if (amountVnd > 2_000_000_000L) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_AMOUNT_UNSUPPORTED",
                    "Payment amount exceeds the PayOS single-payment limit");
        }
        if (expiresAtEpochSeconds > Integer.MAX_VALUE) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_EXPIRY_UNSUPPORTED",
                    "Payment expiry is outside PayOS supported range");
        }
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
        return response.data().checkoutUrl();
    }

    @Override
    public WebhookEvent verifyAndParseWebhook(JsonNode request) {
        JsonNode data = request == null ? null : request.get("data");
        if (data == null || data.isNull()) throw badRequest("PAYMENT_WEBHOOK_INVALID", "Missing PayOS payment data");
        String signature = text(request, "signature");
        String canonicalData = data.properties().stream().sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + scalarValue(entry.getValue()))
                .collect(Collectors.joining("&"));
        if (!verifySignature(signature, canonicalData)) {
            throw badRequest("PAYMENT_SIGNATURE_INVALID", "PayOS webhook signature is invalid");
        }

        String orderCode = text(data, "orderCode");
        String reference = text(data, "reference");
        String eventKey = reference == null || reference.isBlank() ? orderCode : reference;
        if (eventKey == null || eventKey.isBlank() || orderCode == null || orderCode.isBlank()) {
            throw badRequest("PAYMENT_WEBHOOK_INVALID", "Missing PayOS transaction reference");
        }
        long amount = number(data, "amount");
        boolean successful = Boolean.TRUE.equals(booleanValue(request, "success"))
                && "00".equals(text(data, "code"));
        String safePayload = "{\"code\":\"" + escape(text(request, "code")) + "\",\"success\":"
                + Boolean.TRUE.equals(booleanValue(request, "success")) + ",\"data\":{\"orderCode\":\""
                + escape(orderCode) + "\",\"amount\":" + amount + ",\"reference\":\""
                + escape(reference == null ? "" : reference) + "\",\"code\":\""
                + escape(text(data, "code")) + "\"}}";
        return new WebhookEvent(eventKey, orderCode, amount, successful, safePayload);
    }

    private boolean verifySignature(String signature, String canonicalData) {
        if (signature == null || signature.isBlank()) return false;
        String expected = sign(canonicalData);
        return java.security.MessageDigest.isEqual(expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String data) {
        properties.requireChecksumConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getChecksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign PayOS request", ex);
        }
    }

    private record CreateLinkBody(long orderCode, long amount, String description, String cancelUrl,
                                 String returnUrl, long expiredAt, String signature) {}
    private record PayOsCreateResponse(String code, String desc, PayOsLinkData data) {}
    private record PayOsLinkData(String checkoutUrl) {}

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.canConvertToLong()) throw badRequest("PAYMENT_WEBHOOK_INVALID", "Invalid PayOS " + field);
        return value.longValue();
    }

    private static Boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isBoolean() ? null : value.booleanValue();
    }

    private static String scalarValue(JsonNode value) {
        if (value == null || value.isNull()) return "";
        return value.isTextual() ? value.stringValue() : value.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static AuthException badRequest(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }
}
