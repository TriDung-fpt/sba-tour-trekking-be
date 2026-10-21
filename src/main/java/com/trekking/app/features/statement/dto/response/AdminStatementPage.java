package com.trekking.app.features.statement.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminStatementPage(List<Item> items, int page, int size, long totalItems, int totalPages) {
    public record Item(long id, long companyId, String companyName, LocalDate periodStart, LocalDate periodEnd,
            OffsetDateTime cutoffAt, String status, long payableVnd, long transferredVnd, long remainingVnd,
            String note, List<Line> items, List<Transfer> transfers) {}
    public record Line(long bookingId, String bookingCode, String kind, long retainedRevenueVnd,
            long platformFeeVnd, long heldAmountVnd, long payableVnd) {}
    public record Transfer(long amountVnd, String bankReference, OffsetDateTime transferredAt, String evidenceUrl) {}
}
