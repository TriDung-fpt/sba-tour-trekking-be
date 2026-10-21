package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record BookingPaymentStatus(boolean paymentEnabled, String bookingStatus, long totalPriceVnd,
        long receivedVnd, long pendingReviewVnd, long refundedVnd, long amountDueVnd, List<Attempt> attempts) {
    public record Attempt(long id, String purpose, long amountVnd, String status, OffsetDateTime expiresAt) {}
}
