package com.trekking.app.features.tour.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.company.entity.Guide;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "schedule_guides", schema = "trekking")
@IdClass(ScheduleGuideId.class)
public class ScheduleGuide extends TimestampedEntity {

    @Id
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Id
    @Column(name = "guide_id", nullable = false)
    private Long guideId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "is_lead", nullable = false)
    private Boolean isLead = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "schedule_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "company_id", referencedColumnName = "company_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private TourSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "guide_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "company_id", referencedColumnName = "company_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private Guide guide;
}
