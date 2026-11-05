package com.trekking.app.features.booking.dto.request;

import com.trekking.app.features.booking.enums.BookingStatus;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/** Nullable, conjunctive operator filters; the created interval is [createdFrom, createdTo). */
public record BookingSearchCriteria(
        BookingStatus status,
        Long scheduleId,
        Long tourId,
        Long customerId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo
) {}
