package com.trekking.app.features.payment.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.payment.enums.RefundChannel;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_refunds", schema = "trekking")
public class CustomerRefund extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "refund_request_id", nullable = false)
    private Long refundRequestId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "original_payment_id")
    private Long originalPaymentId;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, columnDefinition = "text")
    private RefundChannel channel;

    @Column(name = "transaction_reference", nullable = false, length = 200)
    private String transactionReference;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "refunded_at", nullable = false)
    private OffsetDateTime refundedAt;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "evidence_url", nullable = false, columnDefinition = "text")
    private String evidenceUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount recordedByAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "refund_request_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "booking_id", referencedColumnName = "booking_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private RefundRequest refundRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumns({
        @JoinColumn(name = "original_payment_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "booking_id", referencedColumnName = "booking_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private CustomerPayment originalPayment;
}
