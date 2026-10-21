package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.PaymentNotice;
import com.trekking.app.features.payment.enums.GatewayProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentNoticeRepository extends JpaRepository<PaymentNotice, Long> {
    boolean existsByProviderAndEventKey(GatewayProvider provider, String eventKey);

    @Modifying
    @Transactional
    @Query(value = "insert into trekking.payment_notices(attempt_id,provider,event_key,signature_valid,payload,received_at,processed_at) " +
            "values (:attemptId,'payos',:eventKey,true,cast(:payload as jsonb),:now,:now) " +
            "on conflict(provider,event_key) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("attemptId") Long attemptId, @Param("eventKey") String eventKey,
                       @Param("payload") String payload, @Param("now") java.time.OffsetDateTime now);
}
