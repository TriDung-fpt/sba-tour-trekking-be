package com.trekking.app.features.booking.service;

import com.trekking.app.features.booking.dto.request.CancelBookingRequest;
import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.response.BookingDetailResponse;
import com.trekking.app.features.booking.dto.response.BookingPaymentContext;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.dto.response.BookingSummaryResponse;
import org.springframework.data.domain.Page;

public interface BookingService {
    BookingDetailResponse create(CreateBookingRequest request);
    Page<BookingSummaryResponse> myBookings(int page, int size);
    BookingDetailResponse get(long bookingId);
    BookingDetailResponse cancel(long bookingId, CancelBookingRequest request);
    int expirePendingBookings();

    /** Internal ownership-scoped price snapshot for Payment. */
    BookingPaymentContext getPaymentContext(long bookingId, long customerId);

    /** Trusted server call after Payment verifies its cumulative accepted receipts. */
    BookingStatus confirmDeposit(long bookingId, long verifiedPaidTotalVnd);
}
