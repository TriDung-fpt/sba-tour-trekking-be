package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.CustomerRefund;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CustomerRefundRepository extends Repository<CustomerRefund, Long> {
    CustomerRefund save(CustomerRefund refund);

    @Query(value = "select coalesce(sum(amount_vnd), 0) from trekking.customer_refunds where booking_id = :bookingId", nativeQuery = true)
    BigDecimal refundedTotal(@Param("bookingId") long bookingId);
}
