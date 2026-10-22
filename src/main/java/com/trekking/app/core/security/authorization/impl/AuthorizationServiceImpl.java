package com.trekking.app.core.security.authorization.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.account.dto.response.AuthorizationResponse;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AccountFinancialPermissionRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.company.repository.CompanyMemberRepository;
import com.trekking.app.features.company.repository.CompanyRepository;
import com.trekking.app.features.company.repository.GuideRepository;
import com.trekking.app.features.tour.repository.ScheduleGuideRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthorizationServiceImpl implements AuthorizationService {
    private static final Set<Permission> ACCOUNT_PERMISSIONS = Set.of(
            Permission.ACCOUNT_READ, Permission.ACCOUNT_BLOCK, Permission.ACCOUNT_ROLES_WRITE,
            Permission.COMPANY_REVIEW, Permission.POLICY_MANAGE);
    private static final Set<Permission> COMPANY_PERMISSIONS = Set.of(
            Permission.COMPANY_READ, Permission.COMPANY_MANAGE, Permission.COMPANY_OPERATE);

    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final GuideRepository guides;
    private final ScheduleGuideRepository assignments;
    private final AccountFinancialPermissionRepository financialPermissions;

    public AuthorizationServiceImpl(AppAccountRepository accounts, AccountRoleRepository roles,
                                CompanyRepository companies, CompanyMemberRepository members,
                                GuideRepository guides, ScheduleGuideRepository assignments,
                                AccountFinancialPermissionRepository financialPermissions) {
        this.accounts = accounts;
        this.roles = roles;
        this.companies = companies;
        this.members = members;
        this.guides = guides;
        this.assignments = assignments;
        this.financialPermissions = financialPermissions;
    }

    public long currentAccountId() { return actor().accountId(); }

    public Set<RoleType> currentRoles() { return actor().roles(); }

    public void require(Permission permission) {
        Actor actor = actor();
        if (permission != null && permission.isFinancial()) {
            if (!actor.roles().contains(RoleType.ACCOUNTANT)
                    || !financialPermissions.existsByAccountIdAndPermission(actor.accountId(), permission)) throw forbidden();
            return;
        }
        // Scoped actions can only be checked through the corresponding resource guard.
        if (!ACCOUNT_PERMISSIONS.contains(permission) || !actor.roles().contains(RoleType.ADMIN)) throw forbidden();
    }

    public void requireCompany(Permission permission, long companyId) {
        Actor actor = actor();
        if (!COMPANY_PERMISSIONS.contains(permission)) throw forbidden();
        if (!actor.roles().contains(RoleType.COMPANY_MANAGER)) throw resourceNotFound();
        var member = members.findByCompanyIdAndAccountIdAndActiveTrue(companyId, actor.accountId())
                .orElseThrow(AuthorizationServiceImpl::resourceNotFound);
        if (member.getMemberRole() != CompanyMemberRole.OWNER && member.getMemberRole() != CompanyMemberRole.MANAGER) {
            throw resourceNotFound();
        }
        var company = companies.findById(companyId).orElseThrow(AuthorizationServiceImpl::resourceNotFound);
        if (permission == Permission.COMPANY_OPERATE && company.getStatus() != CompanyStatus.APPROVED) {
            throw resourceNotFound();
        }
    }

    public void requireGuideSchedule(long scheduleId) {
        Actor actor = actor();
        if (!actor.roles().contains(RoleType.GUIDE)) throw resourceNotFound();
        var guide = guides.findByAccountIdAndActiveTrue(actor.accountId())
                .orElseThrow(AuthorizationServiceImpl::resourceNotFound);
        var company = companies.findById(guide.getCompanyId()).orElseThrow(AuthorizationServiceImpl::resourceNotFound);
        if (company.getStatus() != CompanyStatus.APPROVED
                || !assignments.existsByScheduleIdAndGuideIdAndCompanyId(scheduleId, guide.getId(), company.getId())) {
            throw resourceNotFound();
        }
    }

    public AuthorizationResponse authorization() {
        Actor actor = actor();
        var granted = EnumSet.noneOf(Permission.class);
        if (actor.roles().contains(RoleType.ADMIN)) granted.addAll(ACCOUNT_PERMISSIONS);
        if (actor.roles().contains(RoleType.ACCOUNTANT)) {
            financialPermissions.findAllByAccountId(actor.accountId()).forEach(grant -> granted.add(grant.getPermission()));
        }
        var permissions = granted.stream().map(Enum::name).sorted().toList();
        var contexts = actor.roles().contains(RoleType.COMPANY_MANAGER)
                ? members.findActiveContexts(actor.accountId()).stream()
                    .filter(m -> m.getMemberRole() == CompanyMemberRole.OWNER || m.getMemberRole() == CompanyMemberRole.MANAGER)
                    .map(m -> new AuthorizationResponse.CompanyContext(m.getCompanyId(),
                            m.getCompany().getStatus().getValue(), m.getMemberRole().getValue())).toList()
                : java.util.List.<AuthorizationResponse.CompanyContext>of();
        return new AuthorizationResponse(actor.accountId(),
                actor.roles().stream().map(RoleType::getValue).sorted().toList(), permissions, contexts);
    }

    private Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) throw AuthException.invalidSession();
        var account = accounts.findById(principal.accountId()).orElseThrow(AuthException::invalidSession);
        if (account.getStatus() != AccountStatus.ACTIVE || account.getEmailVerifiedAt() == null
                || Boolean.TRUE.equals(account.getMustChangePassword())) throw AuthException.invalidSession();
        var currentRoles = EnumSet.noneOf(RoleType.class);
        roles.findAllByAccountId(account.getId()).forEach(role -> currentRoles.add(role.getRole()));
        return new Actor(account.getId(), Set.copyOf(currentRoles));
    }

    private record Actor(long accountId, Set<RoleType> roles) {}

    private static AuthException forbidden() {
        return new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Action is not permitted");
    }

    private static AuthException resourceNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }
}
