package com.trekking.app.features.payment.service;

import com.trekking.app.features.payment.enums.PaymentProvider;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

/** Provider adapter. Each gateway owns its link format and webhook verification/parsing. */
public interface PaymentGateway {
    PaymentProvider provider();

    String createLink(String merchantReference, long amountVnd, String description, OffsetDateTime expiresAt);

    WebhookEvent verifyAndParseWebhook(JsonNode request);

    /** {@code payer} is the signed source account of the transfer, or null when the provider did not report it. */
    record WebhookEvent(String eventKey, String merchantReference, long amountVnd,
                        boolean successful, String safePayload, PayerAccount payer) {}

    /** Bank account the customer paid from. {@code accountName} may be null for banks that omit it. */
    record PayerAccount(String bankBin, String bankName, String accountNumber, String accountName) {}
}
