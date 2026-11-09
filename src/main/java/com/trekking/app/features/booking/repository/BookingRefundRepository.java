package com.trekking.app.features.booking.repository;

import com.trekking.app.features.payment.entity.CustomerRefund;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Read-only refund access for booking totals. */
public interface BookingRefundRepository extends Repository<CustomerRefund, Long> {
    List<CustomerRefund> findByBookingIdIn(Collection<Long> bookingIds);
}
