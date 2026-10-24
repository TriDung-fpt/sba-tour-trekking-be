package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;

public record PaymentLinkResponse(long attemptId, String checkoutUrl, String merchantReference,
                                  long amountVnd, String status, OffsetDateTime expiresAt) {}
