package com.trekking.app.features.statement.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PartnerStatementPage(List<Item> items, int page, int size, long totalItems, int totalPages) {
    public record Item(long id, LocalDate periodStart, LocalDate periodEnd, String status,
            long payableVnd, long transferredVnd, long remainingVnd, List<Transfer> transfers, String note) {}
    public record Transfer(long amountVnd, OffsetDateTime transferredAt) {}
}
