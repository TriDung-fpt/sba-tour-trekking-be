package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.PaymentAttempt;
import com.trekking.app.features.payment.enums.PaymentStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends Repository<PaymentAttempt, Long> {
    @Query("select a from PaymentAttempt a where a.id = :id")
    Optional<PaymentAttempt> findById(@Param("id") Long id);

    @Query("""
            select a from PaymentAttempt a
            where (:status is null or a.status = :status)
              and (:bookingId is null or a.bookingId = :bookingId)
            order by a.id desc
            """)
    Page<PaymentAttempt> search(@Param("status") PaymentStatus status, @Param("bookingId") Long bookingId,
                                Pageable pageable);
}
