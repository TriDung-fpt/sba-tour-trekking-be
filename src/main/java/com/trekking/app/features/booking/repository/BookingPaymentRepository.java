package com.trekking.app.features.booking.repository;

import com.trekking.app.features.payment.entity.CustomerPayment;
import com.trekking.app.features.payment.enums.AllocationStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Read-only payment access for booking totals. */
public interface BookingPaymentRepository extends Repository<CustomerPayment, Long> {
    List<CustomerPayment> findByBookingIdInAndAllocationStatus(Collection<Long> bookingIds, AllocationStatus status);
}
