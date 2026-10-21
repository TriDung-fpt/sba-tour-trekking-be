package com.trekking.app.features.tour.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PartnerDeparturePage(List<Item> items, int page, int size, long totalItems, int totalPages) {
    public record Item(long scheduleId, long tourId, String tourTitle, OffsetDateTime startsAt,
            OffsetDateTime endsAt, String status, String meetingPoint, String contactPhone,
            int capacity, int heldSeats, int confirmedSeats, int availableSeats,
            long participantCount, List<String> guideNames) {}
}
