package com.trekking.app.features.payment.service.impl;

import com.trekking.app.features.payment.config.PayOsProperties;
import com.trekking.app.features.payment.service.PayOsPayoutGateway;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class PayOsPayoutGatewayImpl implements PayOsPayoutGateway {
    private final PayOsProperties properties;
    private final RestClient client;

    public PayOsPayoutGatewayImpl(PayOsProperties properties) {
        this.properties = properties;
        this.client = RestClient.builder().baseUrl(properties.getApiBaseUrl()).build();
    }

    @Override
    public PayoutResult create(String referenceId, String idempotencyKey, long amountVnd,
                               String description, String bankBin, String accountNumber) {
        properties.requirePayoutConfigured();
        var body = Map.of("referenceId", referenceId, "amount", amountVnd,
                "description", description, "toBin", bankBin, "toAccountNumber", accountNumber);
        JsonNode response = client.post().uri("/v1/payouts")
                .header("x-client-id", properties.getPayoutClientId())
                .header("x-api-key", properties.getPayoutApiKey())
                .header("x-idempotency-key", idempotencyKey)
                .header("x-signature", sign(body))
                .body(body).retrieve().body(JsonNode.class);
        return result(response);
    }

    @Override
    public java.util.Optional<PayoutResult> findByReference(String referenceId) {
        properties.requirePayoutConfigured();
        JsonNode response = client.get().uri(uri -> uri.path("/v1/payouts").queryParam("referenceId", referenceId).build())
                .header("x-client-id", properties.getPayoutClientId())
                .header("x-api-key", properties.getPayoutApiKey())
                .retrieve().body(JsonNode.class);
        JsonNode payouts = response == null ? null : response.path("data").path("payouts");
        JsonNode first = first(payouts);
        return first == null ? java.util.Optional.empty() : java.util.Optional.of(result(first, response));
    }

    @Override
    public java.util.Optional<PayoutResult> findById(String payoutId) {
        properties.requirePayoutConfigured();
        JsonNode response = client.get().uri("/v1/payouts/{payoutId}", payoutId)
                .header("x-client-id", properties.getPayoutClientId())
                .header("x-api-key", properties.getPayoutApiKey())
                .retrieve().body(JsonNode.class);
        return response == null || response.path("data").isMissingNode()
                ? java.util.Optional.empty() : java.util.Optional.of(result(response));
    }

    private String sign(Map<String, ?> values) {
        try {
            var sorted = new TreeMap<String, String>();
            values.forEach((key, value) -> sorted.put(key, value == null ? "" : String.valueOf(value)));
            String data = sorted.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining("&"));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getPayoutChecksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign PayOS payout request", ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%7E", "~");
    }

    private static PayoutResult result(JsonNode response) {
        return result(response == null ? null : response.path("data"), response);
    }

    private static PayoutResult result(JsonNode data, JsonNode ignored) {
        if (data == null || data.isMissingNode()) throw new IllegalStateException("PayOS returned no payout data");
        String id = text(data, "id");
        String state = text(data, "approvalState");
        JsonNode first = first(data.path("transactions"));
        if (state == null && first != null) state = text(first, "state");
        String transactionReference = first == null ? null : text(first, "id");
        return new PayoutResult(id, transactionReference, state == null ? "PROCESSING" : state);
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static JsonNode first(JsonNode values) {
        if (values == null) return null;
        if (values.isArray()) return values.isEmpty() ? null : values.get(0);
        if (values.isObject()) return values.properties().stream().findFirst().map(Map.Entry::getValue).orElse(null);
        return null;
    }
}
