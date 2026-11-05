package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.PaymentNotice;
import com.trekking.app.features.payment.enums.GatewayProvider;
import org.springframework.data.repository.Repository;

public interface PaymentNoticeRepository extends Repository<PaymentNotice, Long> {
    PaymentNotice save(PaymentNotice notice);
    boolean existsByProviderAndEventKey(GatewayProvider provider, String eventKey);
}
