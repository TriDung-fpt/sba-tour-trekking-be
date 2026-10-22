package com.trekking.app.features.account.entity;

import com.trekking.app.core.security.authorization.Permission;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AccountFinancialPermissionId implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long accountId;
    private Permission permission;
}
