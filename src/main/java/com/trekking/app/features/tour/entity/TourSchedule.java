package com.trekking.app.features.tour.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tour_schedules", schema = "trekking")
public class TourSchedule extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "held_seats", nullable = false)
    private Integer heldSeats = 0;

    @Column(name = "confirmed_seats", nullable = false)
    private Integer confirmedSeats = 0;

    @Column(name = "base_price_vnd", nullable = false)
    private Long basePriceVnd;

    @Column(name = "equipment_fee_vnd", nullable = false)
    private Long equipmentFeeVnd = 0L;

    @Column(name = "deposit_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal depositPercent;

    @Column(name = "balance_due_at", nullable = false)
    private OffsetDateTime balanceDueAt;

    @Column(name = "meeting_point", columnDefinition = "text")
    private String meetingPoint;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    @Column(name = "actual_completed_at")
    private OffsetDateTime actualCompletedAt;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "tour_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "company_id", referencedColumnName = "company_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private Tour tour;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "completed_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount completedByAccount;
}
