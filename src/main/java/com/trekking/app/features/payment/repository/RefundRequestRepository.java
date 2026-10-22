package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.RefundRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Locked refund reads used by the shared finance authorization boundary. */
public interface RefundRequestRepository extends Repository<RefundRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :refundId")
    Optional<RefundRequest> findForUpdate(@Param("refundId") long refundId);
}
