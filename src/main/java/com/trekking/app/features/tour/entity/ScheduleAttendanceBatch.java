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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "schedule_attendance_batches", schema = "trekking")
public class ScheduleAttendanceBatch extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "round_type", nullable = false, columnDefinition = "text")
    private String roundType;

    @Column(name = "round_status", nullable = false, columnDefinition = "text")
    private String roundStatus = "CONFIRMED";

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "submitted_by_account_id", nullable = false)
    private Long submittedByAccountId;

    @Column(name = "submitted_by_guide_id", nullable = false)
    private Long submittedByGuideId;

    @Column(name = "general_note", columnDefinition = "text")
    private String generalNote;
}
