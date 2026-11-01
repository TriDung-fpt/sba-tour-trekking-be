package com.trekking.app.features.policy.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "policy_version_revocations", schema = "trekking")
@Getter
@Setter
public class PolicyVersionRevocation {
    @Id
    @Column(name = "policy_version_id")
    private Long policyVersionId;
    @Column(name = "revoked_by", nullable = false)
    private Long revokedBy;
    @Column(name = "revoked_at", nullable = false)
    private OffsetDateTime revokedAt;
    @Column(nullable = false)
    private String reason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_version_id", insertable = false, updatable = false)
    private PolicyVersion version;
}
