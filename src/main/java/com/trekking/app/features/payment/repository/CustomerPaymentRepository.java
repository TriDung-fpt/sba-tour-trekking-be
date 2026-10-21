package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.CustomerPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Long> {
    @Query("select coalesce(sum(p.amountVnd), 0) from CustomerPayment p where p.bookingId = :bookingId and p.allocationStatus = com.trekking.app.features.payment.enums.AllocationStatus.ACCEPTED")
    long acceptedTotal(@Param("bookingId") long bookingId);
    @Query("select coalesce(sum(p.amountVnd), 0) from CustomerPayment p where p.bookingId = :bookingId")
    long receivedTotal(@Param("bookingId") long bookingId);
    @Query("select coalesce(sum(p.amountVnd), 0) from CustomerPayment p where p.bookingId = :bookingId and p.allocationStatus = com.trekking.app.features.payment.enums.AllocationStatus.PENDING_REVIEW")
    long pendingReviewTotal(@Param("bookingId") long bookingId);
    Optional<CustomerPayment> findByAttemptId(Long attemptId);
    Optional<CustomerPayment> findByProviderAndTransactionReference(com.trekking.app.features.payment.enums.PaymentProvider provider,
            String transactionReference);
    Page<CustomerPayment> findAllByAllocationStatus(com.trekking.app.features.payment.enums.AllocationStatus status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomerPayment p where p.id = :id")
    Optional<CustomerPayment> lockById(@Param("id") long id);
}
