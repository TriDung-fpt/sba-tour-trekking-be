package com.trekking.app.features.tour.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.company.entity.Company;
import com.trekking.app.features.company.entity.PolicyVersion;
import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.TourStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tours", schema = "trekking")
public class Tour extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", columnDefinition = "text")
    private DifficultyLevel difficulty;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "required_experience", columnDefinition = "text")
    private String requiredExperience;

    @Column(name = "included_services", columnDefinition = "text")
    private String includedServices;

    @Column(name = "excluded_services", columnDefinition = "text")
    private String excludedServices;

    @Column(name = "route_file_url", columnDefinition = "text")
    private String routeFileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private TourStatus status = TourStatus.DRAFT;

    @Column(name = "cancellation_policy_version_id")
    private Long cancellationPolicyVersionId;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cancellation_policy_version_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private PolicyVersion cancellationPolicyVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewed_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount reviewedByAccount;
}
