package com.trekking.app.features.tour.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "participant_equipment_handovers", schema = "trekking")
public class ParticipantEquipmentHandover extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "allocated_quantity", nullable = false)
    private Integer allocatedQuantity;

    @Column(name = "check_in_condition", nullable = false, columnDefinition = "text")
    private String checkInCondition;

    @Column(name = "check_in_note", columnDefinition = "text")
    private String checkInNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "check_in_photos_json", nullable = false, columnDefinition = "jsonb")
    private String checkInPhotosJson = "[]";

    @Column(name = "handed_over_at", nullable = false)
    private OffsetDateTime handedOverAt;

    @Column(name = "returned_quantity")
    private Integer returnedQuantity;

    @Column(name = "check_out_condition", columnDefinition = "text")
    private String checkOutCondition;

    @Column(name = "check_out_note", columnDefinition = "text")
    private String checkOutNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "check_out_photos_json", nullable = false, columnDefinition = "jsonb")
    private String checkOutPhotosJson = "[]";

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;
}
