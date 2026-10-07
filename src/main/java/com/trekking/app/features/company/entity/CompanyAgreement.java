package com.trekking.app.features.company.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.OffsetDateTime;
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
@Table(name = "company_agreements", schema = "trekking")
public class CompanyAgreement extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "policy_version_id", nullable = false)
    private Long policyVersionId;

    @Column(name = "accepted_by", nullable = false)
    private Long acceptedBy;

    @Column(name = "accepted_at", nullable = false)
    private OffsetDateTime acceptedAt;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "accepted_ip", columnDefinition = "inet")
    private InetAddress acceptedIp;

    @Column(name = "email_verified_at", nullable = false)
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "platform_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal platformFeePercent = new BigDecimal("10.00");

    @Column(name = "payout_wait_hours", nullable = false)
    private Integer payoutWaitHours = 72;

    @Column(name = "payout_terms_snapshot", nullable = false, columnDefinition = "text")
    private String payoutTermsSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_version_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private PolicyVersion policyVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accepted_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount acceptedByAccount;
}
