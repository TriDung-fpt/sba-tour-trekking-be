package com.trekking.app.features.review.repository;

import com.trekking.app.features.support.entity.Review;
import com.trekking.app.features.support.enums.ReviewStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {
    Optional<Review> findByBookingId(long bookingId);

    @Query("""
            select r from Review r
            join fetch r.booking b
            join fetch b.schedule s
            join fetch s.tour
            join fetch b.customer
            where r.id in :ids
            """)
    List<Review> findDetailsByIds(@Param("ids") Collection<Long> ids);

    @Query("""
            select r from Review r
            join fetch r.booking b
            join fetch b.schedule s
            join fetch s.tour
            join fetch b.customer
            where r.id = :id
            """)
    Optional<Review> findDetailById(@Param("id") long id);

    @Query(value = """
            select r from Review r
            join r.booking b
            join b.schedule s
            where s.tourId = :tourId and r.status = :status
            """,
            countQuery = """
            select count(r) from Review r
            join r.booking b
            join b.schedule s
            where s.tourId = :tourId and r.status = :status
            """)
    Page<Review> findVisibleByTourId(@Param("tourId") long tourId, @Param("status") ReviewStatus status, Pageable pageable);

    @Query("""
            select coalesce(avg(r.tourRating), 0.0)
            from Review r
            join r.booking b
            join b.schedule s
            where s.tourId = :tourId and r.status = :status
            """)
    Double averageTourRating(@Param("tourId") long tourId, @Param("status") ReviewStatus status);

    @Query("""
            select count(r)
            from Review r
            join r.booking b
            join b.schedule s
            where s.tourId = :tourId and r.status = :status
            """)
    long countReviewsForTourAndStatus(@Param("tourId") long tourId, @Param("status") ReviewStatus status);
}
