package com.trekking.app.features.payment;

import com.trekking.app.features.payment.config.PayOsProperties;
import com.trekking.app.features.payment.service.PaymentGateway.PayerAccount;
import com.trekking.app.features.payment.service.PaymentGateway.WebhookEvent;
import com.trekking.app.features.payment.service.impl.PayOsGateway;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayOsGatewayTests {
    private static final String CHECKSUM_KEY = "test-checksum-key";
    private final JsonMapper json = JsonMapper.builder().build();
    private final PayOsGateway gateway = new PayOsGateway(properties());

    @Test
    void webhookExposesSignedPayerAccountButKeepsItOutOfTheStoredPayload() {
        WebhookEvent event = gateway.verifyAndParseWebhook(json.readTree(signed(Map.of(
                "orderCode", 123456, "amount", 30000, "reference", "FT001", "code", "00",
                "counterAccountBankId", "970415", "counterAccountBankName", "VietinBank",
                "counterAccountName", "TRAN THI B", "counterAccountNumber", "9876543210004321"))));

        assertThat(event.payer()).isEqualTo(new PayerAccount("970415", "VietinBank", "9876543210004321", "TRAN THI B"));
        assertThat(event.safePayload()).doesNotContain("9876543210004321").doesNotContain("TRAN THI B");
    }

    @Test
    void missingOrMalformedPayerAccountIsIgnored() {
        WebhookEvent missing = gateway.verifyAndParseWebhook(json.readTree(signed(Map.of(
                "orderCode", 123456, "amount", 30000, "reference", "FT002", "code", "00",
                "counterAccountBankId", "", "counterAccountNumber", ""))));
        WebhookEvent malformed = gateway.verifyAndParseWebhook(json.readTree(signed(Map.of(
                "orderCode", 123456, "amount", 30000, "reference", "FT003", "code", "00",
                "counterAccountBankId", "VCB", "counterAccountNumber", "12-34"))));

        assertThat(missing.payer()).isNull();
        assertThat(malformed.payer()).isNull();
    }

    @Test
    void tamperedPayerAccountFailsSignatureCheck() {
        String body = signed(Map.of("orderCode", 123456, "amount", 30000, "reference", "FT004", "code", "00",
                "counterAccountBankId", "970415", "counterAccountNumber", "9876543210004321"));
        String tampered = body.replace("9876543210004321", "1111111111111111");

        assertThatThrownBy(() -> gateway.verifyAndParseWebhook(json.readTree(tampered)))
                .hasMessageContaining("signature");
    }

    private String signed(Map<String, Object> data) {
        String canonical = new TreeMap<>(data).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return json.writeValueAsString(Map.of("code", "00", "desc", "success", "success", true,
                "data", data, "signature", hmac(canonical)));
    }

    private static String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CHECKSUM_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static PayOsProperties properties() {
        PayOsProperties properties = new PayOsProperties();
        properties.setChecksumKey(CHECKSUM_KEY);
        return properties;
    }
}
