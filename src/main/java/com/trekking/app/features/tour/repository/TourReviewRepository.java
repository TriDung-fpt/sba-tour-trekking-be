package com.trekking.app.features.tour.repository;

import com.trekking.app.features.support.entity.Review;
import com.trekking.app.features.support.enums.ReviewStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourReviewRepository extends JpaRepository<Review, Long> {
    // Aggregate results need a query; Spring Data method names cannot express AVG and COUNT together.
    @Query("select avg(r.tourRating), count(r) from Review r where r.booking.schedule.tourId = :tourId and r.status = :status")
    List<Object[]> summarizePublicReviews(@Param("tourId") Long tourId, @Param("status") ReviewStatus status);

    List<Review> findAllByBooking_Schedule_TourIdAndStatusOrderByCreatedAtDescIdDesc(
            Long tourId, ReviewStatus status, Pageable pageable);

    long countByBooking_Schedule_TourIdAndStatus(Long tourId, ReviewStatus status);
}
