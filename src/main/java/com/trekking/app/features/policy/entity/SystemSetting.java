package com.trekking.app.features.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "system_settings", schema = "trekking")
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "change_reason", nullable = false, columnDefinition = "text")
    private String changeReason;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
