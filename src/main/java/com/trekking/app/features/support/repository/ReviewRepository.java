package com.trekking.app.features.support.repository;

import com.trekking.app.features.support.entity.Review;
import com.trekking.app.features.support.enums.ReviewStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> lockById(@Param("id") Long id);

    @Query("""
            select r from Review r
            where (:status is null or r.status = :status)
              and (:bookingId is null or r.bookingId = :bookingId)
            order by r.id desc
            """)
    Page<Review> search(@Param("status") ReviewStatus status, @Param("bookingId") Long bookingId,
                        Pageable pageable);
}
