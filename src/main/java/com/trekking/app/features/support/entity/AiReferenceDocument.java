package com.trekking.app.features.support.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.policy.entity.PolicyVersion;
import com.trekking.app.features.support.enums.DocumentFileType;
import com.trekking.app.features.support.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_reference_documents", schema = "trekking")
public class AiReferenceDocument extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "file_url", nullable = false, columnDefinition = "text")
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, columnDefinition = "text")
    private DocumentFileType fileType;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber = 1;

    @Column(name = "previous_version_id")
    private Long previousVersionId;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "policy_version_id")
    private Long policyVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount uploadedByAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "policy_version_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private PolicyVersion policyVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "previous_version_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AiReferenceDocument previousVersion;
}
