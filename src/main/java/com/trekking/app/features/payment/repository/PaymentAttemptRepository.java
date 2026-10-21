package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.PaymentAttempt;
import com.trekking.app.features.payment.enums.PaymentPurpose;
import com.trekking.app.features.payment.enums.PaymentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByMerchantReference(String merchantReference);
    List<PaymentAttempt> findAllByBookingIdOrderByCreatedAtDesc(Long bookingId);
    boolean existsByBookingIdAndPurposeAndStatusAndExpiresAtAfter(Long bookingId, PaymentPurpose purpose,
            PaymentStatus status, OffsetDateTime now);

    boolean existsByBookingIdAndPurposeAndStatusInAndExpiresAtAfter(Long bookingId, PaymentPurpose purpose,
            List<PaymentStatus> statuses, OffsetDateTime now);
}
