package com.trekking.app.features.support.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.support.enums.ChatStatus;
import com.trekking.app.features.tour.entity.Tour;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chats", schema = "trekking")
public class Chat extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "assigned_staff_id")
    private Long assignedStaffId;

    @Column(name = "current_tour_id")
    private Long currentTourId;

    @Column(name = "current_booking_id")
    private Long currentBookingId;

    @Column(name = "support_request_id")
    private Long supportRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private ChatStatus status = ChatStatus.AI;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assigned_staff_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount assignedStaff;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "current_tour_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Tour currentTour;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "current_booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Booking currentBooking;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "support_request_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private SupportRequest supportRequest;
}
