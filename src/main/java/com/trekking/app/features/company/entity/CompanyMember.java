package com.trekking.app.features.company.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "company_members", schema = "trekking")
@IdClass(CompanyMemberId.class)
public class CompanyMember extends TimestampedEntity {

    @Id
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Id
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, columnDefinition = "text")
    private CompanyMemberRole memberRole = CompanyMemberRole.MANAGER;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount account;
}
