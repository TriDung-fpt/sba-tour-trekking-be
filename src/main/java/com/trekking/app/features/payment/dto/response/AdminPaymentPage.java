package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminPaymentPage(List<Item> items, int page, int size, long totalItems, int totalPages) {
    public record Item(long id, long bookingId, String bookingCode, String provider, String transactionReference,
            long amountVnd, OffsetDateTime receivedAt, Attempt attempt) {}
    public record Attempt(String purpose, long requestedAmountVnd) {}
}
