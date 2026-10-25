package com.trekking.app.features.tour.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rental_package_template_items", schema = "trekking",
        uniqueConstraints = @UniqueConstraint(name = "uq_rental_package_template_equipment", columnNames = {"package_template_id", "equipment_id"}))
public class RentalPackageTemplateItem extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_template_id", nullable = false)
    private Long packageTemplateId;

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
