package com.trekking.app.features.booking.service;

import com.trekking.app.features.booking.dto.response.PartnerBookingDetail;
import com.trekking.app.features.booking.dto.response.PartnerBookingPage;
import com.trekking.app.features.booking.dto.request.CheckInRequest;
import com.trekking.app.features.booking.dto.request.CancelBookingRequest;
import com.trekking.app.features.booking.dto.response.BookingActionResponse;
import java.time.OffsetDateTime;

public interface PartnerBookingService {
    PartnerBookingPage list(long accountId, String status, OffsetDateTime fromDate, OffsetDateTime toDate,
                            String query, int page, int size);
    PartnerBookingDetail detail(long accountId, long bookingId);
    BookingActionResponse checkIn(long accountId, long bookingId, long participantId, CheckInRequest request);
    BookingActionResponse undoCheckIn(long accountId, long bookingId, long participantId, String reason);
    BookingActionResponse cancel(long accountId, long bookingId, CancelBookingRequest request);
}
