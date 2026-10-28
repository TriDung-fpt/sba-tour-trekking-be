package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends Repository<PaymentAttempt, Long> {
    PaymentAttempt save(PaymentAttempt attempt);

    Optional<PaymentAttempt> findFirstByBookingIdAndPurposeAndStatusOrderByCreatedAtDesc(
            long bookingId, com.trekking.app.features.payment.enums.PaymentPurpose purpose,
            com.trekking.app.features.payment.enums.PaymentStatus status);

    Optional<PaymentAttempt> findByMerchantReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttempt a where a.merchantReference = :reference")
    Optional<PaymentAttempt> findByMerchantReferenceForUpdate(@Param("reference") String reference);
}
