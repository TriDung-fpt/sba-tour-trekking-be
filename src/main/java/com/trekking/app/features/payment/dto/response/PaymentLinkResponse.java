package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;

public record PaymentLinkResponse(boolean enabled, Long attemptId, String purpose, Long amountVnd,
        String status, String checkoutUrl, String qrCode, OffsetDateTime expiresAt) {}
