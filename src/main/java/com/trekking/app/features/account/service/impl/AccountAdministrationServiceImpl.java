package com.trekking.app.features.account.service.impl;

import com.trekking.app.features.account.service.AccountAdministrationService;
import com.trekking.app.features.account.dto.response.AdminAccountResponse;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.dto.request.AccountAdministrationRequests;
import com.trekking.app.features.account.entity.AccountRole;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.service.audit.AccountAuditState;
import com.trekking.app.features.account.service.audit.AuditWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdministrationServiceImpl implements AccountAdministrationService {
    private static final Set<RoleType> ASSIGNABLE = EnumSet.of(RoleType.CUSTOMER, RoleType.GUIDE);

    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final AuthorizationService authorization;
    private final AuditWriter audits;
    private final EntityManager entities;

    public AccountAdministrationServiceImpl(AppAccountRepository accounts, AccountRoleRepository roles,
                                        AuthorizationService authorization, AuditWriter audits, EntityManager entities) {
        this.accounts = accounts;
        this.roles = roles;
        this.authorization = authorization;
        this.audits = audits;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public AdminAccountResponse get(Long id) {
        authorization.require(Permission.ACCOUNT_READ);
        var account = accounts.findById(id).orElseThrow(AccountAdministrationServiceImpl::notFound);
        return response(account, roleNames(roles.findAllByAccountId(id)));
    }

    @Transactional
    public AdminAccountResponse block(Long id, String inputReason) {
        requireMutation(Permission.ACCOUNT_BLOCK);
        String reason = reason(inputReason);
        AppAccount account = mutableAccount(id);
        var accountRoles = roles.findAllByAccountId(id);
        requireStatus(account, AccountStatus.ACTIVE);
        var names = roleNames(accountRoles);
        var before = state(account, names);
        account.setStatus(AccountStatus.BLOCKED);
        account.setBlockedReason(reason);
        account.setBlockedBy(authorization.currentAccountId());
        // Persisted in the same transaction as blocking; old sessions stay invalid after unblocking.
        account.setSessionVersion(Math.addExact(account.getSessionVersion(), 1L));
        audits.accountChanged(authorization.currentAccountId(), id, AuditWriter.AccountAction.ACCOUNT_BLOCKED,
                reason, before, state(account, names));
        return response(account, names);
    }

    @Transactional
    public AdminAccountResponse unblock(Long id, String inputReason) {
        requireMutation(Permission.ACCOUNT_BLOCK);
        String reason = reason(inputReason);
        AppAccount account = mutableAccount(id);
        var accountRoles = roles.findAllByAccountId(id);
        requireStatus(account, AccountStatus.BLOCKED);
        var names = roleNames(accountRoles);
        var before = state(account, names);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBlockedReason(null);
        account.setBlockedBy(null);
        audits.accountChanged(authorization.currentAccountId(), id, AuditWriter.AccountAction.ACCOUNT_UNBLOCKED,
                reason, before, state(account, names));
        return response(account, names);
    }

    @Transactional
    public AdminAccountResponse replaceRoles(Long id, AccountAdministrationRequests.Roles request) {
        requireMutation(Permission.ACCOUNT_ROLES_WRITE);
        String reason = reason(request.reason());
        Set<RoleType> requested = parseRoles(request.roles());
        AppAccount account = mutableAccount(id);
        var existing = roles.findAllByAccountId(id);
        var before = state(account, roleNames(existing));
        var removed = existing.stream().filter(role -> !requested.contains(role.getRole())).toList();
        roles.deleteAll(removed);
        var previous = EnumSet.noneOf(RoleType.class);
        existing.forEach(role -> previous.add(role.getRole()));
        var added = new ArrayList<AccountRole>();
        requested.stream().filter(role -> !previous.contains(role)).forEach(type -> {
            var role = new AccountRole();
            role.setAccountId(id);
            role.setRole(type);
            added.add(role);
        });
        roles.saveAll(added);
        var names = requested.stream().map(RoleType::getValue).sorted().toList();
        audits.accountChanged(authorization.currentAccountId(), id, AuditWriter.AccountAction.ACCOUNT_ROLES_CHANGED,
                reason, before, state(account, names));
        return response(account, names);
    }

    private AppAccount mutableAccount(Long id) {
        if (id != null && authorization.currentAccountId() == id.longValue()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "SELF_ACCOUNT_MUTATION_FORBIDDEN",
                    "You cannot administratively modify your own account");
        }
        return accounts.lockById(id).orElseThrow(AccountAdministrationServiceImpl::notFound);
    }

    private void requireMutation(Permission permission) {
        long actorId = authorization.currentAccountId();
        accounts.serializeAdministration();
        var actor = accounts.findById(actorId).orElseThrow(AuthException::invalidSession);
        // Recheck after waiting: another admin may have blocked or demoted this caller.
        entities.refresh(actor, LockModeType.PESSIMISTIC_WRITE);
        authorization.require(permission);
        // Self-mutation remains forbidden, so the active calling admin is preserved.
    }

    private static void requireStatus(AppAccount account, AccountStatus expected) {
        if (account.getStatus() != expected) {
            throw new AuthException(HttpStatus.CONFLICT, "INVALID_ACCOUNT_TRANSITION",
                    "Account status does not allow this transition");
        }
    }

    private static String reason(String input) {
        String value = input == null ? null : input.strip();
        if (value == null || value.isBlank() || value.length() > 2000) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REASON",
                    "A reason of at most 2000 characters is required");
        }
        return value;
    }

    private static Set<RoleType> parseRoles(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > ASSIGNABLE.size()) throw invalidRoles();
        var result = EnumSet.noneOf(RoleType.class);
        for (String value : values) {
            var role = ASSIGNABLE.stream().filter(type -> type.getValue().equals(value)).findFirst()
                    .orElseThrow(AccountAdministrationServiceImpl::invalidRoles);
            if (!result.add(role)) throw invalidRoles();
        }
        return result;
    }

    private static AuthException invalidRoles() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_ROLES",
                "Roles must be distinct supported non-administrator roles");
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account was not found");
    }

    private static List<String> roleNames(List<AccountRole> accountRoles) {
        return accountRoles.stream().map(role -> role.getRole().getValue()).sorted().toList();
    }

    private static AccountAuditState state(AppAccount account, List<String> roleNames) {
        return new AccountAuditState(account.getStatus().getValue(), account.getBlockedReason(),
                account.getBlockedBy(), roleNames);
    }

    private static AdminAccountResponse response(AppAccount account, List<String> roleNames) {
        return new AdminAccountResponse(account.getId(), account.getEmail(), account.getFullName(), account.getPhone(),
                roleNames, account.getStatus().getValue(), account.getBlockedReason(), account.getBlockedBy(),
                Boolean.TRUE.equals(account.getMustChangePassword()), account.getEmailVerifiedAt());
    }
}
