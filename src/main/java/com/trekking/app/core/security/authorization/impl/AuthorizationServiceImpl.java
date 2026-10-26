package com.trekking.app.core.security.authorization.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.dto.response.AuthorizationResponse;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.repository.GuideRepository;
import com.trekking.app.features.tour.repository.ScheduleGuideRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthorizationServiceImpl implements AuthorizationService {
    private static final Set<Permission> ADMIN_PERMISSIONS = Set.of(
            Permission.ACCOUNT_READ, Permission.ACCOUNT_BLOCK, Permission.ACCOUNT_ROLES_WRITE,
            Permission.ACCOUNT_UPDATE,
            Permission.POLICY_MANAGE, Permission.TOUR_READ, Permission.TOUR_MANAGE,
            Permission.REVIEW_MODERATE,
            Permission.TOUR_COMPOSE, Permission.TOUR_PUBLISH, Permission.SCHEDULE_MANAGE,
            Permission.BOOKING_MANAGE, Permission.GUIDE_MANAGE, Permission.FINANCE_READ,
            Permission.PAYMENT_RECORD, Permission.REFUND_APPROVE, Permission.REFUND_RECORD,
            Permission.DASHBOARD_READ, Permission.REPORT_EXPORT);

    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final GuideRepository guides;
    private final ScheduleGuideRepository assignments;

    public AuthorizationServiceImpl(AppAccountRepository accounts, AccountRoleRepository roles,
                                    GuideRepository guides, ScheduleGuideRepository assignments) {
        this.accounts = accounts;
        this.roles = roles;
        this.guides = guides;
        this.assignments = assignments;
    }

    @Override
    public long currentAccountId() { return actor().accountId(); }

    @Override
    public Set<RoleType> currentRoles() { return actor().roles(); }

    @Override
    public void require(Permission permission) {
        Actor actor = actor();
        if (permission == null || !ADMIN_PERMISSIONS.contains(permission)
                || !actor.roles().contains(RoleType.ADMIN)) throw forbidden();
    }

    @Override
    public void requireGuideSchedule(long scheduleId) {
        Actor actor = actor();
        if (!actor.roles().contains(RoleType.GUIDE)) throw resourceNotFound();
        var guide = guides.findByAccountIdAndActiveTrue(actor.accountId())
                .orElseThrow(AuthorizationServiceImpl::resourceNotFound);
        if (!assignments.existsByScheduleIdAndGuideId(scheduleId, guide.getId())) throw resourceNotFound();
    }

    @Override
    public AuthorizationResponse authorization() {
        Actor actor = actor();
        List<String> permissions = actor.roles().contains(RoleType.ADMIN)
                ? ADMIN_PERMISSIONS.stream().map(Enum::name).sorted().toList() : List.of();
        return new AuthorizationResponse(actor.accountId(),
                actor.roles().stream().map(RoleType::getValue).sorted().toList(), permissions);
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
