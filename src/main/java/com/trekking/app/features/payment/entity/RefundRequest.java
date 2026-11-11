package com.trekking.app.features.payment.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.payment.enums.RefundChannel;
import com.trekking.app.features.payment.enums.RefundStatus;
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
@Table(name = "refund_requests", schema = "trekking")
public class RefundRequest extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "requested_amount_vnd", nullable = false)
    private Long requestedAmountVnd;

    @Column(name = "approved_amount_vnd")
    private Long approvedAmountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private RefundStatus status = RefundStatus.REQUESTED;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "paid_amount_vnd")
    private Long paidAmountVnd;

    @Column(name = "refund_percent")
    private Integer refundPercent;

    @Column(name = "hours_before_departure")
    private Long hoursBeforeDeparture;

    @Column(name = "bank_bin", length = 20)
    private String bankBin;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "recipient_name_ciphertext", columnDefinition = "text")
    private String recipientNameCiphertext;

    @Column(name = "recipient_account_ciphertext", columnDefinition = "text")
    private String recipientAccountCiphertext;

    @Column(name = "recipient_account_last4", length = 4)
    private String recipientAccountLast4;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_channel", columnDefinition = "text")
    private RefundChannel payoutChannel;

    @Column(name = "payout_reference_id", length = 100)
    private String payoutReferenceId;

    @Column(name = "payout_id", length = 200)
    private String payoutId;

    @Column(name = "payout_state", length = 50)
    private String payoutState;

    @Column(name = "payout_initiated_by")
    private Long payoutInitiatedBy;

    @Column(name = "payout_last_checked_at")
    private OffsetDateTime payoutLastCheckedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "requested_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount requestedByAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "approved_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount approvedByAccount;
}
