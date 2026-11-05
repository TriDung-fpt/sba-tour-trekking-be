package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.CustomerPayment;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import com.trekking.app.features.payment.enums.AllocationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerPaymentRepository extends Repository<CustomerPayment, Long> {
    CustomerPayment save(CustomerPayment payment);

    @Query(value = "select coalesce(sum(amount_vnd), 0) from trekking.customer_payments where booking_id = :bookingId and allocation_status = 'accepted'", nativeQuery = true)
    BigDecimal acceptedTotal(@Param("bookingId") long bookingId);

    @Query("select p from CustomerPayment p where p.id = :id")
    Optional<CustomerPayment> findById(@Param("id") Long id);

    @Query("""
            select p from CustomerPayment p
            where (:allocation is null or p.allocationStatus = :allocation)
              and (:bookingId is null or p.bookingId = :bookingId)
            order by p.id desc
            """)
    Page<CustomerPayment> search(@Param("allocation") AllocationStatus allocation,
                                 @Param("bookingId") Long bookingId, Pageable pageable);
}
