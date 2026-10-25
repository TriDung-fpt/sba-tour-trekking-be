package com.trekking.app.features.tour.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "tour_equipment_items", schema = "trekking")
public class TourEquipmentItem extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "package_id", nullable = false)
    private Long packageId;
    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;
    @Column(name = "quantity_in_package", nullable = false)
    private Integer quantityInPackage;
    @Column(name = "individual_rental_price_vnd")
    private Long individualRentalPriceVnd;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", insertable = false, updatable = false)
    private Equipment equipment;
}
