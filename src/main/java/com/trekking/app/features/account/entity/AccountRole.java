package com.trekking.app.features.account.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.enums.RoleType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "account_roles", schema = "trekking")
@IdClass(AccountRoleId.class)
public class AccountRole extends TimestampedEntity {

    @Id
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, columnDefinition = "text")
    private RoleType role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount account;
}
