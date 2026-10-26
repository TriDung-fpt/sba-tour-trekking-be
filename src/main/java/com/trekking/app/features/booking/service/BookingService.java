package com.trekking.app.features.booking.service;

import com.trekking.app.features.booking.dto.request.BookingSearchCriteria;
import com.trekking.app.features.booking.dto.request.CancelBookingRequest;
import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.request.ReplacePassengersRequest;
import com.trekking.app.features.booking.dto.request.UpdatePassengerRequest;
import com.trekking.app.features.booking.dto.response.BookingCancellationResponse;
import com.trekking.app.features.booking.dto.response.BookingCreateResult;
import com.trekking.app.features.booking.dto.response.BookingDetailResponse;
import com.trekking.app.features.booking.dto.response.BookingSummaryResponse;
import com.trekking.app.features.booking.dto.response.ScheduleAvailabilityResponse;
import com.trekking.app.features.booking.enums.BookingStatus;
import org.springframework.data.domain.Page;

public interface BookingService {
    BookingCreateResult create(CreateBookingRequest request);

    Page<BookingSummaryResponse> listMine(BookingStatus status, int page, int size);

    Page<BookingSummaryResponse> search(BookingSearchCriteria criteria, int page, int size);

    BookingDetailResponse get(long bookingId);

    BookingDetailResponse replacePassengers(long bookingId, ReplacePassengersRequest request);

    BookingDetailResponse updatePassenger(long bookingId, long participantId, UpdatePassengerRequest request);

    BookingCancellationResponse cancel(long bookingId, CancelBookingRequest request);

    ScheduleAvailabilityResponse availability(long scheduleId);
}
