package com.trekking.app.features.support.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.entity.Participant;
import com.trekking.app.features.support.enums.ReviewStatus;
import jakarta.persistence.*;
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
@Table(name = "reviews", schema = "trekking")
public class Review extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "tour_rating", nullable = false)
    private Short tourRating;

    @Column(name = "service_rating", nullable = false)
    private Short serviceRating;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "equipment_feedback", columnDefinition = "text")
    private String equipmentFeedback;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", nullable = false, columnDefinition = "jsonb")
    private String imageUrls = "[]";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private ReviewStatus status = ReviewStatus.VISIBLE;

    @Column(name = "moderated_by")
    private Long moderatedBy;

    @Column(name = "moderation_reason", columnDefinition = "text")
    private String moderationReason;

    @Column(name = "operator_reply", columnDefinition = "text")
    private String operatorReply;

    @Column(name = "replied_by")
    private Long repliedBy;

    @Column(name = "replied_at")
    private OffsetDateTime repliedAt;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "author_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private Booking booking;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "participant_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "booking_id", referencedColumnName = "booking_id", insertable = false, updatable = false),
        @JoinColumn(name = "author_id", referencedColumnName = "account_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private Participant participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "moderated_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount moderatedByAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "replied_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount repliedByAccount;
}
