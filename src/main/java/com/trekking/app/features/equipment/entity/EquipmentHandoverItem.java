package com.trekking.app.features.equipment.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment_handover_items", schema = "trekking")
public class EquipmentHandoverItem extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "handover_record_id", nullable = false)
    private Long handoverRecordId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "condition", nullable = false)
    private String condition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handover_record_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private EquipmentHandoverRecord handoverRecord;
}
