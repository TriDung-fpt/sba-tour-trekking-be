package com.trekking.app.features.booking.service.impl;

import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.BookingScheduleRepository;
import com.trekking.app.features.booking.service.BookingHoldService;
import com.trekking.app.features.tour.entity.TourSchedule;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingHoldServiceImpl implements BookingHoldService {
    private static final int BATCH_SIZE = 100;

    private final BookingRepository bookings;
    private final BookingScheduleRepository schedules;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    public BookingHoldServiceImpl(BookingRepository bookings, BookingScheduleRepository schedules,
                                  PlatformTransactionManager transactionManager, Clock clock) {
        this.bookings = bookings;
        this.schedules = schedules;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public int expireDueHolds(long scheduleId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Long> ids = bookings.findDueHoldIdsForSchedule(scheduleId, BookingStatus.PENDING_PAYMENT, now,
                PageRequest.of(0, BATCH_SIZE));
        int expired = 0;
        for (Long id : ids) {
            if (Boolean.TRUE.equals(newTransaction.execute(status -> expire(id, now)))) expired++;
        }
        return expired;
    }

    // Lock order: Booking, then Schedule. Re-check under lock so repeated runs are no-ops.
    private boolean expire(long bookingId, OffsetDateTime now) {
        Booking booking = bookings.findForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT
                || booking.getHoldExpiresAt().isAfter(now)) {
            return false;
        }
        TourSchedule schedule = schedules.findForUpdate(booking.getScheduleId()).orElseThrow();
        schedule.setHeldSeats(schedule.getHeldSeats() - booking.getParticipantCount());
        booking.setStatus(BookingStatus.EXPIRED);
        return true;
    }
}
