package com.trekking.app.features.booking.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.policy.entity.PolicyVersion;
import com.trekking.app.features.tour.entity.TourSchedule;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bookings", schema = "trekking")
public class Booking extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_code", nullable = false, length = 40)
    private String bookingCode;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Column(name = "includes_equipment", nullable = false)
    private Boolean includesEquipment;

    @Column(name = "base_price_vnd", nullable = false)
    private Long basePriceVnd;

    @Column(name = "equipment_fee_vnd", nullable = false)
    private Long equipmentFeeVnd;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Setter(AccessLevel.NONE)
    @Column(name = "total_price_vnd", insertable = false, updatable = false)
    private Long totalPriceVnd;

    @Column(name = "deposit_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal depositPercent;

    @Column(name = "cancellation_policy_version_id", nullable = false)
    private Long cancellationPolicyVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "jsonb")
    private String policySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equipment_snapshot", nullable = false, columnDefinition = "jsonb")
    private String equipmentSnapshot = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(name = "hold_expires_at", nullable = false)
    private OffsetDateTime holdExpiresAt;

    @Column(name = "balance_due_at", nullable = false)
    private OffsetDateTime balanceDueAt;

    @Column(name = "price_locked_at")
    private OffsetDateTime priceLockedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancellation_policy_version_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private PolicyVersion cancellationPolicyVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cancelled_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount cancelledByAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private TourSchedule schedule;

}
