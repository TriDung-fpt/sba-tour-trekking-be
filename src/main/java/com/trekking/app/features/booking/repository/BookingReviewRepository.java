package com.trekking.app.features.booking.repository;

import com.trekking.app.features.support.entity.Review;
import org.springframework.data.repository.Repository;

/** Read-only review lookup for the booking authorization contract. */
public interface BookingReviewRepository extends Repository<Review, Long> {
    boolean existsByBookingId(long bookingId);
}
