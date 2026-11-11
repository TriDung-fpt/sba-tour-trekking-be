package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.CustomerRefund;
import com.trekking.app.features.payment.enums.RefundChannel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface CustomerRefundRepository extends Repository<CustomerRefund, Long> {
    CustomerRefund save(CustomerRefund refund);

    boolean existsByChannelAndTransactionReference(RefundChannel channel, String transactionReference);

    Optional<CustomerRefund> findFirstByRefundRequestId(long refundRequestId);

    List<CustomerRefund> findAllByBookingId(long bookingId);
}
