package com.trekking.app.features.booking.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PartnerBookingDetail(long id, String bookingCode, String status, OffsetDateTime createdAt,
        Departure departure, Customer customer, List<Participant> participants, Money money,
        List<RefundRequest> refundRequests) {
    public record Departure(long scheduleId, long tourId, String tourTitle, OffsetDateTime startsAt,
            OffsetDateTime endsAt, String meetingPoint, String contactPhone) {}
    public record Customer(String displayName, String email, String phone) {}
    public record Participant(long id, String fullName, String email, String phone,
            OffsetDateTime checkedInAt, String checkInNote) {}
    public record Money(long totalPriceVnd, long receivedVnd, long pendingReviewVnd, long refundedVnd,
            BigDecimal depositPercent, OffsetDateTime balanceDueAt) {}
    public record RefundRequest(long id, long requestedAmountVnd, Long approvedAmountVnd,
            String status, String reason, String decisionNote, OffsetDateTime createdAt) {}
}
