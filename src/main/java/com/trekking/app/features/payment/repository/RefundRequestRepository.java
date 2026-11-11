package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.RefundRequest;
import com.trekking.app.features.payment.enums.RefundStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

public interface RefundRequestRepository extends Repository<RefundRequest, Long> {
    RefundRequest save(RefundRequest refund);

    Optional<RefundRequest> findById(long id);

    /** Locked refund reads used by the shared finance authorization boundary. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefundRequest> findForUpdateById(long refundId);

    Optional<RefundRequest> findFirstByBookingIdOrderByCreatedAtDescIdDesc(long bookingId);

    boolean existsByBookingIdAndStatusIn(long bookingId, Collection<RefundStatus> statuses);

    Page<RefundRequest> findAllBy(Pageable pageable);

    Page<RefundRequest> findAllByStatus(RefundStatus status, Pageable pageable);
}
