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
@Table(name = "tour_required_items", schema = "trekking")
public class PackingItem extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = true;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Tour tour;
}
