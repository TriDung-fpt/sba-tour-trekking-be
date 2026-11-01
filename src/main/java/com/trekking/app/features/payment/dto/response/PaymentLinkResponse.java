package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;
import com.trekking.app.features.payment.enums.PaymentProvider;

public record PaymentLinkResponse(long attemptId, PaymentProvider provider, String checkoutUrl, String merchantReference,
                                  long amountVnd, String status, OffsetDateTime expiresAt) {}
