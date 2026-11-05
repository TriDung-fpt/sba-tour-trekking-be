package com.trekking.app.features.tour.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tour_participant_checkouts", schema = "trekking")
public class TourParticipantCheckout {
    @Id
    @Column(name = "participant_id")
    private Long participantId;

    @Column(name = "checked_out_at", nullable = false)
    private OffsetDateTime checkedOutAt;

    @Column(name = "checked_out_by", nullable = false)
    private Long checkedOutBy;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}
