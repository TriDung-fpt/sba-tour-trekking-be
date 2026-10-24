package com.trekking.app.features.account.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.service.BusinessAuthorizationService;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class BusinessAuthorizationServiceImpl implements BusinessAuthorizationService {
    private final AuthorizationService authorization;
    private final BookingRepository bookings;
    private final ParticipantRepository participants;
    private final EntityManager entityManager;
    private final Clock clock;

    public BusinessAuthorizationServiceImpl(AuthorizationService authorization, BookingRepository bookings,
                                            ParticipantRepository participants, EntityManager entityManager, Clock clock) {
        this.authorization = authorization;
        this.bookings = bookings;
        this.participants = participants;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public void requirePayment(long bookingId) {
        var booking = ownedBooking(bookingId);
        var schedule = lockedSchedule(booking);
        var now = OffsetDateTime.now(clock);
        if ((booking.getStatus() != BookingStatus.PENDING_PAYMENT && booking.getStatus() != BookingStatus.CONFIRMED)
                || (booking.getStatus() == BookingStatus.PENDING_PAYMENT && !booking.getHoldExpiresAt().isAfter(now))
                || !booking.getBalanceDueAt().isAfter(now)
                || schedule.getStatus() != ScheduleStatus.SCHEDULED || !schedule.getStartsAt().isAfter(now)
                || BigDecimal.valueOf(booking.getTotalPriceVnd())
                    .compareTo(bookings.acceptedPaymentTotalVnd(bookingId)) <= 0) {
            throw conflict("PAYMENT_NOT_ALLOWED", "Booking is not eligible for payment");
        }
    }

    @Override
    public void requireReview(long bookingId) {
        var booking = ownedBooking(bookingId);
        var schedule = lockedSchedule(booking);
        var now = OffsetDateTime.now(clock);
        if (booking.getStatus() != BookingStatus.COMPLETED || schedule.getStatus() != ScheduleStatus.COMPLETED
                || schedule.getActualCompletedAt() == null || schedule.getActualCompletedAt().isAfter(now)) {
            throw conflict("REVIEW_NOT_ALLOWED", "Booking is not eligible for review");
        }
        var participant = participants.findByBookingIdAndAccountId(bookingId, booking.getCustomerId())
                .orElseThrow(() -> conflict("REVIEW_NOT_ALLOWED", "Booking is not eligible for review"));
        entityManager.refresh(participant, LockModeType.PESSIMISTIC_READ);
        if (!booking.getId().equals(participant.getBookingId())
                || !booking.getCustomerId().equals(participant.getAccountId())
                || participant.getCheckedInAt() == null || participant.getCheckedInBy() == null
                || participant.getCheckedInAt().isAfter(now)) {
            throw conflict("REVIEW_NOT_ALLOWED", "Booking is not eligible for review");
        }
        if (bookings.hasReview(bookingId)) {
            throw conflict("REVIEW_ALREADY_EXISTS", "A review already exists for this booking");
        }
    }

    private Booking ownedBooking(long bookingId) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Business authorization requires a write transaction");
        }
        long accountId = authorization.currentAccountId();
        if (!authorization.currentRoles().contains(RoleType.CUSTOMER)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Action is not permitted");
        }
        var booking = bookings.findOwnedForUpdate(bookingId, accountId).orElseThrow(() ->
                new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found"));
        // A lock query alone can return an already managed entity with stale state.
        entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
        return booking;
    }

    private TourSchedule lockedSchedule(Booking booking) {
        var schedule = booking.getSchedule();
        // Keep one lock order for every guard: booking, schedule, participant.
        entityManager.refresh(schedule, LockModeType.PESSIMISTIC_READ);
        return schedule;
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}
