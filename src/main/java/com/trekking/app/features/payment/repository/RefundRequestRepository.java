package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.RefundRequest;
import com.trekking.app.features.payment.enums.RefundStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    List<RefundRequest> findAllByBookingIdOrderByCreatedAtDesc(Long bookingId);
    Optional<RefundRequest> findByIdempotencyKey(String idempotencyKey);
    Page<RefundRequest> findAllByStatus(RefundStatus status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> lockById(@Param("id") long id);
    @Query(value = "select coalesce(sum(case when r.status='requested' then r.requested_amount_vnd else "
            + "greatest(coalesce(r.approved_amount_vnd,0)-coalesce(x.refunded_vnd,0),0) end),0) "
            + "from trekking.refund_requests r left join (select refund_request_id,sum(amount_vnd) refunded_vnd "
            + "from trekking.customer_refunds group by refund_request_id) x on x.refund_request_id=r.id "
            + "where r.booking_id=:bookingId and r.status in ('requested','approved','processing') "
            + "and (:excludeId is null or r.id<>:excludeId)", nativeQuery = true)
    long reservedAmount(@Param("bookingId") long bookingId, @Param("excludeId") Long excludeId);
}
