package com.trekking.app.features.payment.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.payment.enums.AllocationStatus;
import com.trekking.app.features.payment.enums.PaymentProvider;
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
@Table(name = "customer_payments", schema = "trekking")
public class CustomerPayment extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "attempt_id")
    private Long attemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, columnDefinition = "text")
    private PaymentProvider provider;

    @Column(name = "transaction_reference", nullable = false, length = 200)
    private String transactionReference;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Column(name = "gateway_fee_vnd", nullable = false)
    private Long gatewayFeeVnd = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_status", nullable = false, columnDefinition = "text")
    private AllocationStatus allocationStatus = AllocationStatus.PENDING_REVIEW;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "evidence_url", columnDefinition = "text")
    private String evidenceUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "verified_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount verifiedByAccount;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumns({
        @JoinColumn(name = "attempt_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "booking_id", referencedColumnName = "booking_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private PaymentAttempt attempt;
}
