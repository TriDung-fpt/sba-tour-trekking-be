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
@Table(name = "tour_equipment_packages", schema = "trekking")
public class TourEquipmentPackage extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "package_template_id")
    private Long packageTemplateId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "included_items", nullable = false, columnDefinition = "text")
    private String includedItems;

    @Column(name = "shared_items", columnDefinition = "text")
    private String sharedItems;

    @Column(name = "personal_items_to_bring", columnDefinition = "text")
    private String personalItemsToBring;

    @Column(name = "rental_price_vnd")
    private Long rentalPriceVnd;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Tour tour;
}
