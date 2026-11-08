package com.trekking.app.features.account.service.impl;

import com.trekking.app.features.account.service.AccountAdministrationService;
import com.trekking.app.features.account.dto.response.AdminAccountListItemResponse;
import com.trekking.app.features.account.dto.response.AdminAccountPageResponse;
import com.trekking.app.features.account.dto.response.AdminAccountResponse;
import com.trekking.app.features.account.dto.response.GuideOptionResponse;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.dto.request.AccountAdministrationRequests;
import com.trekking.app.features.account.entity.AccountRole;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.GuideRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.service.audit.AccountAuditState;
import com.trekking.app.features.account.service.audit.AuditWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdministrationServiceImpl implements AccountAdministrationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final Set<RoleType> ASSIGNABLE = EnumSet.of(RoleType.CUSTOMER, RoleType.GUIDE);
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdat", "createdAt", "fullname", "fullName", "email", "email");

    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final AuthorizationService authorization;
    private final AuditWriter audits;
    private final EntityManager entities;
    private final GuideRepository guides;

    public AccountAdministrationServiceImpl(AppAccountRepository accounts, AccountRoleRepository roles,
                                        AuthorizationService authorization, AuditWriter audits, EntityManager entities,
                                        GuideRepository guides) {
        this.accounts = accounts;
        this.roles = roles;
        this.authorization = authorization;
        this.audits = audits;
        this.entities = entities;
        this.guides = guides;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideOptionResponse> listGuides() {
        authorization.require(Permission.GUIDE_MANAGE);
        return guides.findActiveGuides().stream().map(g -> new GuideOptionResponse(g.getId(), g.getId(), g.getAccountId(),
                g.getAccount().getFullName(), g.getAccount().getEmail(), g.getAccount().getPhone())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAccountPageResponse list(int page, int size, String query, String role, String status, String sort) {
        authorization.require(Permission.ACCOUNT_READ);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidRequest("page must be >= 0 and size must be between 1 and 100");
        }
        RoleType roleFilter = parseRoleFilter(role);
        AccountStatus statusFilter = parseStatusFilter(status);
        String pattern = likePattern(query);
        var result = accounts.findAll(filtered(roleFilter, statusFilter, pattern),
                PageRequest.of(page, size, parseSort(sort)));
        var rolesByAccount = roleLabels(result.getContent().stream().map(AppAccount::getId).toList());
        var items = result.getContent().stream().map(account -> new AdminAccountListItemResponse(
                account.getId(), account.getFullName(), account.getEmail(), account.getPhone(),
                rolesByAccount.getOrDefault(account.getId(), List.of()), account.getStatus().getValue(),
                account.getCreatedAt(), null)).toList();
        return new AdminAccountPageResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
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
        if (requested.contains(RoleType.GUIDE) && guides.findByAccountId(id).isEmpty()) {
            var guide = new com.trekking.app.features.account.entity.Guide();
            guide.setAccountId(id);
            guide.setActive(true);
            guides.save(guide);
        }
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

    private static AuthException invalidRequest(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    private static Specification<AppAccount> filtered(RoleType role, AccountStatus status, String pattern) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (role != null) {
                var roles = query.subquery(Long.class);
                var roleRoot = roles.from(AccountRole.class);
                roles.select(roleRoot.get("accountId")).where(
                        cb.equal(roleRoot.get("accountId"), root.get("id")),
                        cb.equal(roleRoot.get("role"), role));
                predicates.add(cb.exists(roles));
            }
            if (pattern != null) {
                var email = cb.like(cb.lower(root.get("email")), cb.literal(pattern), '\\');
                var name = cb.like(cb.lower(root.get("fullName")), cb.literal(pattern), '\\');
                var phone = cb.like(cb.lower(root.get("phone")), cb.literal(pattern), '\\');
                predicates.add(cb.or(email, name, phone));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<Long, List<String>> roleLabels(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        var grouped = new LinkedHashMap<Long, List<String>>();
        for (Object[] row : roles.findRoleLabelsByAccountIds(accountIds)) {
            long accountId = ((Number) row[0]).longValue();
            grouped.computeIfAbsent(accountId, ignored -> new ArrayList<>()).add(String.valueOf(row[1]));
        }
        grouped.values().forEach(Collections::sort);
        return grouped;
    }

    private static RoleType parseRoleFilter(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        for (RoleType type : RoleType.values()) {
            if (type.name().equalsIgnoreCase(value) || type.getValue().equalsIgnoreCase(value)) return type;
        }
        throw invalidRequest("role must be ADMIN, GUIDE, or CUSTOMER");
    }

    private static AccountStatus parseStatusFilter(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        for (AccountStatus type : AccountStatus.values()) {
            if (type.name().equalsIgnoreCase(value) || type.getValue().equalsIgnoreCase(value)) return type;
        }
        throw invalidRequest("status must be ACTIVE, BLOCKED, or DISABLED");
    }

    private static String likePattern(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        if (value.length() > MAX_QUERY_LENGTH) {
            throw invalidRequest("q must be at most 200 characters");
        }
        return "%" + value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static Sort parseSort(String raw) {
        Sort.Direction direction = Sort.Direction.DESC;
        String field = "createdAt";
        if (raw != null && !raw.isBlank()) {
            String[] parts = raw.split(",", -1);
            if (parts.length > 2) throw invalidRequest("sort must be createdAt, fullName, or email");
            field = SORT_FIELDS.get(parts[0].strip().toLowerCase(Locale.ROOT));
            if (field == null) throw invalidRequest("sort must be createdAt, fullName, or email");
            if (parts.length == 2) {
                String requested = parts[1].strip().toLowerCase(Locale.ROOT);
                if ("asc".equals(requested)) direction = Sort.Direction.ASC;
                else if ("desc".equals(requested)) direction = Sort.Direction.DESC;
                else throw invalidRequest("sort direction must be asc or desc");
            }
        }
        return Sort.by(new Sort.Order(direction, field), Sort.Order.desc("id"));
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
                Boolean.TRUE.equals(account.getMustChangePassword()), account.getEmailVerifiedAt(),
                account.getCreatedAt());
    }
}
