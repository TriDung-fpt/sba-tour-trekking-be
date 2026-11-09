package com.trekking.app.features.booking.repository;

import com.trekking.app.features.payment.enums.AllocationStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data repository fragment preserving the existing authorization calls. */
@Transactional(readOnly = true)
public class BookingAuthorizationRepositoryImpl implements BookingAuthorizationRepository {
    private final BookingPaymentRepository payments;
    private final BookingReviewRepository reviews;

    public BookingAuthorizationRepositoryImpl(BookingPaymentRepository payments, BookingReviewRepository reviews) {
        this.payments = payments;
        this.reviews = reviews;
    }

    @Override
    public BigDecimal acceptedPaymentTotalVnd(long bookingId) {
        return payments.findByBookingIdInAndAllocationStatus(List.of(bookingId), AllocationStatus.ACCEPTED).stream()
                .map(payment -> BigDecimal.valueOf(payment.getAmountVnd()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public boolean hasReview(long bookingId) {
        return reviews.existsByBookingId(bookingId);
    }
}
