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
@Table(name = "schedule_participant_attendances", schema = "trekking")
public class ScheduleParticipantAttendance extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "participant_id", nullable = false, unique = true)
    private Long participantId;

    @Column(name = "check_in_batch_id")
    private Long checkInBatchId;

    @Column(name = "check_in_status", nullable = false, columnDefinition = "text")
    private String checkInStatus;

    @Column(name = "check_in_marked_at", nullable = false)
    private OffsetDateTime checkInMarkedAt;

    @Column(name = "check_in_note", columnDefinition = "text")
    private String checkInNote;

    @Column(name = "check_out_batch_id")
    private Long checkOutBatchId;

    @Column(name = "check_out_status", columnDefinition = "text")
    private String checkOutStatus;

    @Column(name = "check_out_marked_at")
    private OffsetDateTime checkOutMarkedAt;

    @Column(name = "check_out_note", columnDefinition = "text")
    private String checkOutNote;
}
