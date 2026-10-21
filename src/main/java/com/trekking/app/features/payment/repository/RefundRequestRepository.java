package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.RefundRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    List<RefundRequest> findAllByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
