package com.trekking.app.features.account.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.dto.request.FinancialPermissionsRequest;
import com.trekking.app.features.account.dto.response.FinancialPermissionsResponse;
import com.trekking.app.features.account.entity.AccountFinancialPermission;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountFinancialPermissionRepository;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.account.service.FinancialPermissionService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class FinancialPermissionServiceImpl implements FinancialPermissionService {
    private static final Set<Permission> SETTLEMENT_DUTIES = Set.of(
            Permission.SETTLEMENT_PREPARE, Permission.SETTLEMENT_APPROVE, Permission.PAYOUT_RECORD);
    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final AccountFinancialPermissionRepository grants;
    private final AuditLogRepository audits;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final ObjectMapper json;

    public FinancialPermissionServiceImpl(AppAccountRepository accounts, AccountRoleRepository roles,
            AccountFinancialPermissionRepository grants, AuditLogRepository audits,
            AuthorizationService authorization, Clock clock, ObjectMapper json) {
        this.accounts = accounts;
        this.roles = roles;
        this.grants = grants;
        this.audits = audits;
        this.authorization = authorization;
        this.clock = clock;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialPermissionsResponse get(long accountId) {
        authorization.require(Permission.ACCOUNT_READ);
        accounts.findById(accountId).orElseThrow(FinancialPermissionServiceImpl::notFound);
        return response(accountId);
    }

    @Override
    @Transactional
    public FinancialPermissionsResponse replace(long accountId, FinancialPermissionsRequest request) {
        authorization.require(Permission.ACCOUNT_ROLES_WRITE);
        long actor = authorization.currentAccountId();
        if (actor == accountId) throw error(HttpStatus.FORBIDDEN, "SELF_ACCOUNT_MUTATION_FORBIDDEN", "Cannot change own permissions");
        var account = accounts.lockById(accountId).orElseThrow(FinancialPermissionServiceImpl::notFound);
        var currentRoles = roles.findAllByAccountId(accountId).stream().map(role -> role.getRole()).toList();
        if (currentRoles.contains(RoleType.ADMIN)) {
            throw error(HttpStatus.FORBIDDEN, "ADMIN_ACCOUNT_PROTECTED", "Administrator accounts cannot receive financial grants");
        }
        var desired = parse(request.permissions());
        String reason = request.reason();
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REASON", "A reason of at most 2000 characters is required");
        }
        if (!desired.isEmpty() && (!currentRoles.contains(RoleType.ACCOUNTANT)
                || account.getStatus() != AccountStatus.ACTIVE || account.getEmailVerifiedAt() == null
                || Boolean.TRUE.equals(account.getMustChangePassword()))) {
            throw error(HttpStatus.CONFLICT, "FINANCIAL_ACCOUNT_INELIGIBLE", "An active verified accountant is required");
        }
        var previous = grants.findAllByAccountId(accountId);
        var before = previous.stream().map(grant -> grant.getPermission().name()).sorted().toList();
        // Delete incompatible old duties before inserting replacements; the DB also enforces separation.
        grants.deleteAll(previous.stream().filter(grant -> !desired.contains(grant.getPermission())).toList());
        grants.flush();
        var existing = previous.stream().map(AccountFinancialPermission::getPermission).toList();
        var now = OffsetDateTime.now(clock);
        desired.stream().filter(permission -> !existing.contains(permission)).forEach(permission -> {
            var grant = new AccountFinancialPermission();
            grant.setAccountId(accountId);
            grant.setPermission(permission);
            grant.setGrantedBy(actor);
            grant.setGrantedAt(now);
            grants.save(grant);
        });
        grants.flush();
        var after = desired.stream().map(Enum::name).sorted().toList();
        var audit = new AuditLog();
        audit.setActorId(actor);
        audit.setEntityType("account");
        audit.setEntityId(accountId);
        audit.setAction("ACCOUNT_FINANCIAL_PERMISSIONS_CHANGED");
        audit.setReason(reason);
        audit.setOccurredAt(now);
        audit.setDetails(json.writeValueAsString(Map.of("before", before, "after", after)));
        audits.saveAndFlush(audit);
        return new FinancialPermissionsResponse(accountId, after);
    }

    private FinancialPermissionsResponse response(long accountId) {
        return new FinancialPermissionsResponse(accountId, grants.findAllByAccountId(accountId).stream()
                .map(grant -> grant.getPermission().name()).sorted().toList());
    }

    private static Set<Permission> parse(List<String> values) {
        if (values == null || values.size() > 7) throw invalid();
        var result = EnumSet.noneOf(Permission.class);
        for (String value : values) {
            Permission permission;
            try { permission = Permission.valueOf(value == null ? "" : value); }
            catch (IllegalArgumentException ex) { throw invalid(); }
            if (!permission.isFinancial() || !result.add(permission)) throw invalid();
        }
        if (result.stream().filter(SETTLEMENT_DUTIES::contains).count() > 1
                || (result.contains(Permission.REFUND_APPROVE) && result.contains(Permission.REFUND_RECORD))) {
            throw error(HttpStatus.BAD_REQUEST, "CONFLICTING_FINANCIAL_DUTIES", "Prepare, approve and record duties must be separated");
        }
        return result;
    }

    private static AuthException invalid() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_FINANCIAL_PERMISSIONS", "Distinct supported financial permissions are required");
    }

    private static AuthException notFound() {
        return error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account was not found");
    }

    private static AuthException error(HttpStatus status, String code, String message) {
        return new AuthException(status, code, message);
    }
}
