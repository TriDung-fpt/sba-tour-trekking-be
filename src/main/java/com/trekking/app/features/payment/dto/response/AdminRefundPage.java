package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminRefundPage(List<Item> items, int page, int size, long totalItems, int totalPages) {
    public record Item(long id, long bookingId, String bookingCode, long companyId, String companyName,
            long requestedAmountVnd, Long approvedAmountVnd, String status, String reason, String decisionNote,
            OffsetDateTime createdAt, long refundedVnd, List<Refund> refunds) {}
    public record Refund(long id, long amountVnd, String channel, String transactionReference,
            OffsetDateTime refundedAt, String evidenceUrl) {}
}
