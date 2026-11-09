package com.trekking.app.features.booking.service.impl;

import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingPaymentRepository;
import com.trekking.app.features.booking.repository.BookingRefundRepository;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.BookingScheduleRepository;
import com.trekking.app.features.booking.service.BookingPaymentService;
import com.trekking.app.features.payment.entity.CustomerPayment;
import com.trekking.app.features.payment.entity.CustomerRefund;
import com.trekking.app.features.payment.enums.AllocationStatus;
import com.trekking.app.features.tour.entity.TourSchedule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingPaymentServiceImpl implements BookingPaymentService {
    private final BookingRepository bookings;
    private final BookingScheduleRepository schedules;
    private final Clock clock;
    private final BookingPaymentRepository payments;
    private final BookingRefundRepository refunds;

    public BookingPaymentServiceImpl(BookingRepository bookings, BookingScheduleRepository schedules, Clock clock,
                                     BookingPaymentRepository payments, BookingRefundRepository refunds) {
        this.bookings = bookings;
        this.schedules = schedules;
        this.clock = clock;
        this.payments = payments;
        this.refunds = refunds;
    }

    @Override
    @Transactional
    public boolean confirmWhenDepositPaid(long bookingId) {
        Booking booking = bookings.findLockedById(bookingId).orElse(null);
        if (booking == null) return false;
        if (booking.getStatus() == BookingStatus.CONFIRMED) return true;
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) return false;

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!booking.getHoldExpiresAt().isAfter(now)) return false;

        long depositAmount = BigDecimal.valueOf(booking.getTotalPriceVnd())
                .multiply(booking.getDepositPercent())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING)
                .longValueExact();
        if (acceptedPaymentTotalVnd(bookingId).compareTo(BigDecimal.valueOf(depositAmount)) < 0) return false;

        TourSchedule schedule = schedules.findLockedById(booking.getScheduleId()).orElseThrow();
        int seats = booking.getParticipantCount();
        if (schedule.getHeldSeats() < seats) {
            throw new IllegalStateException("Booking held seats are inconsistent with its participant count");
        }
        schedule.setHeldSeats(schedule.getHeldSeats() - seats);
        schedule.setConfirmedSeats(schedule.getConfirmedSeats() + seats);
        booking.setStatus(BookingStatus.CONFIRMED);
        return true;
    }

    @Override
    public BigDecimal acceptedPaymentTotalVnd(long bookingId) {
        return acceptedPaymentTotals(List.of(bookingId)).getOrDefault(bookingId, BigDecimal.ZERO);
    }

    @Override
    public BigDecimal refundedTotalVnd(long bookingId) {
        return refundedTotals(List.of(bookingId)).getOrDefault(bookingId, BigDecimal.ZERO);
    }

    @Override
    public Map<Long, BigDecimal> acceptedPaymentTotals(Collection<Long> bookingIds) {
        if (bookingIds.isEmpty()) return Map.of();
        return payments.findByBookingIdInAndAllocationStatus(bookingIds, AllocationStatus.ACCEPTED).stream()
                .collect(Collectors.toMap(CustomerPayment::getBookingId,
                        payment -> BigDecimal.valueOf(payment.getAmountVnd()), BigDecimal::add));
    }

    @Override
    public Map<Long, BigDecimal> refundedTotals(Collection<Long> bookingIds) {
        if (bookingIds.isEmpty()) return Map.of();
        return refunds.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.toMap(CustomerRefund::getBookingId,
                        refund -> BigDecimal.valueOf(refund.getAmountVnd()), BigDecimal::add));
    }
}
