package com.trekking.app.features.account.entity;

import com.trekking.app.core.security.authorization.Permission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "account_financial_permissions", schema = "trekking")
@IdClass(AccountFinancialPermissionId.class)
public class AccountFinancialPermission {
    @Id
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, columnDefinition = "text")
    private Permission permission;

    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;
}
