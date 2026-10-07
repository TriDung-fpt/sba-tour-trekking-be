package com.trekking.app.features.booking.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.tour.entity.TourSchedule;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "carts", schema = "trekking")
public class Cart extends TimestampedEntity {

    @Id
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Column(name = "includes_equipment", nullable = false)
    private Boolean includesEquipment = false;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private TourSchedule schedule;
}
