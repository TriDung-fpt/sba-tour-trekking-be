package com.trekking.app.features.equipment.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.tour.entity.Equipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment_inventory", schema = "trekking")
public class EquipmentInventory extends TimestampedEntity {
    @Id
    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity = 0;

    @Column(name = "maintenance_quantity", nullable = false)
    private Integer maintenanceQuantity = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private EquipmentCategory category;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Equipment equipment;
}
