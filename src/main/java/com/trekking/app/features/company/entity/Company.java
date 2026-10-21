package com.trekking.app.features.company.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.company.enums.CompanyStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "companies", schema = "trekking")
public class Company extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 250)
    private String name;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "license_file_url", columnDefinition = "text")
    private String licenseFileUrl;

    @Column(name = "representative_name", length = 200)
    private String representativeName;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "business_type", length = 40)
    private String businessType;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tour_regions", nullable = false, columnDefinition = "jsonb")
    private List<String> tourRegions = List.of();

    @Column(name = "safety_rules_accepted_at")
    private OffsetDateTime safetyRulesAcceptedAt;

    @Column(name = "safety_rules_version", length = 40)
    private String safetyRulesVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private CompanyStatus status = CompanyStatus.PENDING;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "approved_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount approvedByAccount;
}
