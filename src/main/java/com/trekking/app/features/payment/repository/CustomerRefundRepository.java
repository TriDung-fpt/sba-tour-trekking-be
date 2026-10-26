package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.CustomerRefund;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRefundRepository extends Repository<CustomerRefund, Long> {
    @Query("select r from CustomerRefund r where r.id = :id")
    Optional<CustomerRefund> findById(@Param("id") Long id);

    @Query("""
            select r from CustomerRefund r
            where (:bookingId is null or r.bookingId = :bookingId)
            order by r.id desc
            """)
    Page<CustomerRefund> search(@Param("bookingId") Long bookingId, Pageable pageable);
}
