package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.CustomerPayment;
import com.trekking.app.features.payment.enums.AllocationStatus;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface CustomerPaymentRepository extends Repository<CustomerPayment, Long> {
    CustomerPayment save(CustomerPayment payment);

    List<CustomerPayment> findAllByBookingIdAndAllocationStatus(long bookingId, AllocationStatus status);
}
