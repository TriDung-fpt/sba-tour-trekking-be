package com.trekking.app.features.payment.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.payment.enums.PaymentProvider;
import com.trekking.app.features.payment.enums.PaymentPurpose;
import com.trekking.app.features.payment.enums.PaymentStatus;
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
@Table(name = "payment_attempts", schema = "trekking")
public class PaymentAttempt extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, columnDefinition = "text")
    private PaymentProvider provider;

    @Column(name = "merchant_reference", nullable = false, length = 150)
    private String merchantReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, columnDefinition = "text")
    private PaymentPurpose purpose;

    @Column(name = "requested_amount_vnd", nullable = false)
    private Long requestedAmountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Booking booking;
}
