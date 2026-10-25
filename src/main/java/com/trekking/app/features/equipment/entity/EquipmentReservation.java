package com.trekking.app.features.equipment.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.tour.entity.TourSchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment_reservations", schema = "trekking")
public class EquipmentReservation extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "selection_key", nullable = false, columnDefinition = "text")
    private String selectionKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "hold_expires_at", nullable = false)
    private OffsetDateTime holdExpiresAt;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Column(name = "rental_fee_vnd", nullable = false)
    private Long rentalFeeVnd = 0L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equipment_snapshot", nullable = false, columnDefinition = "jsonb")
    private String equipmentSnapshot = "{}";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private TourSchedule schedule;
}
