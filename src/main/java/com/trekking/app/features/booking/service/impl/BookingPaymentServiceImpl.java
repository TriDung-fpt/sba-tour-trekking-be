package com.trekking.app.features.booking.service.impl;

import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.BookingScheduleRepository;
import com.trekking.app.features.booking.service.BookingPaymentService;
import com.trekking.app.features.tour.entity.TourSchedule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingPaymentServiceImpl implements BookingPaymentService {
    private final BookingRepository bookings;
    private final BookingScheduleRepository schedules;
    private final Clock clock;

    public BookingPaymentServiceImpl(BookingRepository bookings, BookingScheduleRepository schedules, Clock clock) {
        this.bookings = bookings;
        this.schedules = schedules;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void confirmWhenDepositPaid(long bookingId) {
        Booking booking = bookings.findForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT) return;

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!booking.getHoldExpiresAt().isAfter(now)) return;

        long depositAmount = BigDecimal.valueOf(booking.getTotalPriceVnd())
                .multiply(booking.getDepositPercent())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING)
                .longValueExact();
        if (bookings.acceptedPaymentTotalVnd(bookingId).compareTo(BigDecimal.valueOf(depositAmount)) < 0) return;

        TourSchedule schedule = schedules.findForUpdate(booking.getScheduleId()).orElseThrow();
        int seats = booking.getParticipantCount();
        if (schedule.getHeldSeats() < seats) {
            throw new IllegalStateException("Booking held seats are inconsistent with its participant count");
        }
        schedule.setHeldSeats(schedule.getHeldSeats() - seats);
        schedule.setConfirmedSeats(schedule.getConfirmedSeats() + seats);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPriceLockedAt(now);
    }
}
