package com.trekking.app.features.account.entity;

import com.trekking.app.features.account.enums.RoleType;
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
public class AccountRoleId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long accountId;

    private RoleType role;
}
