package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.dto.request.BookingSearchCriteria;
import com.trekking.app.features.booking.entity.Booking;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Optional operator filters, combined with AND; the created interval is [from, to). */
public final class BookingSpecifications {
    private BookingSpecifications() {}

    public static Specification<Booking> matches(BookingSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> filters = new ArrayList<>();
            if (criteria.status() != null) {
                filters.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.scheduleId() != null) {
                filters.add(builder.equal(root.get("scheduleId"), criteria.scheduleId()));
            }
            if (criteria.tourId() != null) {
                filters.add(builder.equal(root.get("schedule").get("tourId"), criteria.tourId()));
            }
            if (criteria.customerId() != null) {
                filters.add(builder.equal(root.get("customerId"), criteria.customerId()));
            }
            if (criteria.createdFrom() != null) {
                filters.add(builder.greaterThanOrEqualTo(root.get("createdAt"), criteria.createdFrom()));
            }
            if (criteria.createdTo() != null) {
                filters.add(builder.lessThan(root.get("createdAt"), criteria.createdTo()));
            }
            return builder.and(filters.toArray(Predicate[]::new));
        };
    }
}
