package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.CustomerRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRefundRepository extends JpaRepository<CustomerRefund, Long> {
    @Query("select coalesce(sum(r.amountVnd), 0) from CustomerRefund r where r.bookingId = :bookingId")
    long refundedTotal(@Param("bookingId") long bookingId);
}
