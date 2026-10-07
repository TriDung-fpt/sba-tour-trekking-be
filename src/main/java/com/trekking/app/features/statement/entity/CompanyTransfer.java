package com.trekking.app.features.statement.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
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
@Table(name = "company_transfers", schema = "trekking")
public class CompanyTransfer extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "statement_id", nullable = false)
    private Long statementId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Column(name = "bank_reference", nullable = false, length = 200)
    private String bankReference;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "transferred_at", nullable = false)
    private OffsetDateTime transferredAt;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "evidence_url", nullable = false, columnDefinition = "text")
    private String evidenceUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount recordedByAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "statement_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "company_id", referencedColumnName = "company_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private CompanyStatement statement;
}
