package com.trekking.app.features.payment.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "refund_rule_sets", schema = "trekking")
public class RefundRuleSet extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules", nullable = false, columnDefinition = "jsonb")
    private String rules;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public void setEffectiveFrom(OffsetDateTime value) { effectiveFrom = value; }
    public void setRules(String value) { rules = value; }
    public void setCreatedBy(Long value) { createdBy = value; }
}
