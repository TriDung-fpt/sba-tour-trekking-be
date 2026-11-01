package com.trekking.app.features.payment.service;

import com.trekking.app.features.payment.enums.PaymentProvider;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

/** Provider adapter. Each gateway owns its link format and webhook verification/parsing. */
public interface PaymentGateway {
    PaymentProvider provider();

    String createLink(String merchantReference, long amountVnd, String description, OffsetDateTime expiresAt);

    WebhookEvent verifyAndParseWebhook(JsonNode request);

    record WebhookEvent(String eventKey, String merchantReference, long amountVnd,
                        boolean successful, String safePayload) {}
}
