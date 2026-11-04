package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.RefundRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Locked refund reads used by the shared finance authorization boundary. */
public interface RefundRequestRepository extends Repository<RefundRequest, Long> {
    RefundRequest save(RefundRequest refund);

    Optional<RefundRequest> findByBookingId(long bookingId);

    List<RefundRequest> findAllByOrderByCreatedAtDesc();

    List<RefundRequest> findAllByStatusInOrderByCreatedAtAsc(Collection<com.trekking.app.features.payment.enums.RefundStatus> statuses);

    @Query(value = "select coalesce(sum(coalesce(approved_amount_vnd, requested_amount_vnd)), 0) from trekking.refund_requests where booking_id = :bookingId and status in ('approved','processing')", nativeQuery = true)
    BigDecimal committedTotal(@Param("bookingId") long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :refundId")
    Optional<RefundRequest> findForUpdate(@Param("refundId") long refundId);
}
