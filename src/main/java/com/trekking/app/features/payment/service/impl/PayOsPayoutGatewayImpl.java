package com.trekking.app.features.payment.service.impl;

import com.trekking.app.features.payment.config.PayOsProperties;
import com.trekking.app.features.payment.service.PayOsPayoutGateway;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
    public PayoutResult create(String referenceId, String idempotencyKey, long amountVnd, String description,
                               String bankBin, String accountNumber) {
        properties.requirePayoutConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("referenceId", referenceId);
        body.put("amount", amountVnd);
        body.put("description", description);
        body.put("toBin", bankBin);
        body.put("toAccountNumber", accountNumber);
        JsonNode response = client.post().uri("/v1/payouts")
                .header("x-client-id", properties.getPayoutClientId())
                .header("x-api-key", properties.getPayoutApiKey())
                .header("x-idempotency-key", idempotencyKey)
                .header("x-signature", sign(body))
                .body(body).retrieve().body(JsonNode.class);
        return result(response == null ? null : response.path("data"));
    }

    @Override
    public Optional<PayoutResult> findByReference(String referenceId) {
        properties.requirePayoutConfigured();
        JsonNode response = client.get()
                .uri(uri -> uri.path("/v1/payouts").queryParam("referenceId", referenceId).build())
                .header("x-client-id", properties.getPayoutClientId())
                .header("x-api-key", properties.getPayoutApiKey())
                .retrieve().body(JsonNode.class);
        JsonNode first = first(response == null ? null : response.path("data").path("payouts"));
        return first == null ? Optional.empty() : Optional.of(result(first));
    }

    private String sign(Map<String, ?> values) {
        try {
            var sorted = new TreeMap<String, String>();
            values.forEach((k, v) -> sorted.put(k, v == null ? "" : String.valueOf(v)));
            String data = sorted.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));
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

    private static PayoutResult result(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) throw new IllegalStateException("PayOS returned no payout data");
        String state = text(data, "approvalState");
        JsonNode transaction = first(data.path("transactions"));
        if (transaction != null && text(transaction, "state") != null) state = text(transaction, "state");
        return new PayoutResult(text(data, "id"), transaction == null ? null : text(transaction, "id"),
                state == null ? "PROCESSING" : state);
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static JsonNode first(JsonNode values) {
        if (values == null || !values.isArray() || values.isEmpty()) return null;
        return values.get(0);
    }
}
