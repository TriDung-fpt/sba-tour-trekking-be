package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.CustomerPayment;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CustomerPaymentRepository extends Repository<CustomerPayment, Long> {
    CustomerPayment save(CustomerPayment payment);

    @Query(value = "select coalesce(sum(amount_vnd), 0) from trekking.customer_payments where booking_id = :bookingId and allocation_status = 'accepted'", nativeQuery = true)
    BigDecimal acceptedTotal(@Param("bookingId") long bookingId);
}
