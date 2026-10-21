package com.trekking.app.features.booking.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PartnerBookingPage(List<Item> items, int page, int size, long totalItems, int totalPages) {
    public record Item(long id, String bookingCode, String status, OffsetDateTime createdAt,
                       Departure departure, int participantCount, long totalPriceVnd,
                       long receivedVnd, long pendingReviewVnd, long refundedVnd) {}
    public record Departure(long scheduleId, long tourId, String tourTitle,
                            OffsetDateTime startsAt, OffsetDateTime endsAt) {}
}
