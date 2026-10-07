package com.trekking.app.features.tour.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tour_locations", schema = "trekking")
@IdClass(TourLocationId.class)
public class TourLocation extends TimestampedEntity {

    @Id
    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Id
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "visit_order", nullable = false)
    private Integer visitOrder = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Tour tour;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Location location;
}
