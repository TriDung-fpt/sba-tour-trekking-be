package com.trekking.app.features.invitation.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.entity.AccountRole;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.entity.Guide;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.repository.GuideRepository;
import com.trekking.app.features.invitation.dto.request.InvitationRequests;
import com.trekking.app.features.invitation.dto.response.InvitationResponse;
import com.trekking.app.features.invitation.repository.InvitationAuditRepository;
import com.trekking.app.features.invitation.repository.RedisInvitationStore;
import com.trekking.app.features.invitation.service.InvitationMailSender;
import com.trekking.app.features.invitation.service.InvitationService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class InvitationServiceImpl implements InvitationService {
    private static final Logger log = LoggerFactory.getLogger(InvitationServiceImpl.class);
    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final GuideRepository guides;
    private final AuthorizationService authorization;
    private final InvitationAuditRepository audits;
    private final RedisInvitationStore store;
    private final InvitationMailSender mail;
    private final AuthCrypto crypto;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final EntityManager entityManager;

    public InvitationServiceImpl(AppAccountRepository accounts, AccountRoleRepository roles, GuideRepository guides,
                                 AuthorizationService authorization,
                                 InvitationAuditRepository audits, RedisInvitationStore store, InvitationMailSender mail,
                                 AuthCrypto crypto, PasswordEncoder passwords, Clock clock, ObjectMapper json,
                                 PlatformTransactionManager transactionManager, EntityManager entityManager) {
        this.accounts = accounts;
        this.roles = roles;
        this.guides = guides;
        this.authorization = authorization;
        this.audits = audits;
        this.store = store;
        this.mail = mail;
        this.crypto = crypto;
        this.passwords = passwords;
        this.clock = clock;
        this.json = json;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
        // Delivery must observe a committed account even when invoked by another transactional service.
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public InvitationResponse inviteStaff(InvitationRequests.Staff request) {
        authorization.require(Permission.ACCOUNT_ROLES_WRITE);
        if (!RoleType.ADMIN.getValue().equals(request.role())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_INVITATION_ROLE", "Internal staff invitations require the admin role");
        }
        return create(request.email(), request.fullName(), request.phone(), RoleType.ADMIN, null);
    }

    @Override
    public InvitationResponse inviteGuide(InvitationRequests.Guide request) {
        return create(request.email(), request.fullName(), request.phone(), RoleType.GUIDE, request.bio());
    }

    private InvitationResponse create(String email, String name, String phone, RoleType role, String bio) {
        Pending pending;
        try {
            pending = transactions.execute(status -> {
                authorization.require(Permission.ACCOUNT_ROLES_WRITE);
                if (accounts.findByEmail(email).isPresent()) throw duplicateEmail();
                var account = new AppAccount();
                account.setEmail(email);
                account.setFullName(name);
                account.setPhone(phone);
                account.setPasswordHash(passwords.encode(crypto.token()));
                account.setMustChangePassword(true);
                account.setEmailVerifiedAt(null);
                accounts.saveAndFlush(account);
                var grant = new AccountRole();
                grant.setAccountId(account.getId());
                grant.setRole(role);
                roles.saveAndFlush(grant);
                // Role inserts update authorization_version through a database trigger.
                entityManager.refresh(account);
                if (role == RoleType.GUIDE) {
                    var guide = new Guide();
                    guide.setAccountId(account.getId());
                    guide.setBio(bio);
                    guides.saveAndFlush(guide);
                }
                audit(authorization.currentAccountId(), account.getId(), "INVITATION_CREATED", role);
                return pending(account);
            });
        } catch (DataIntegrityViolationException ex) {
            // The losing concurrent transaction has already rolled back, including its roles/guide/audit.
            if (accounts.findByEmail(email).isPresent()) throw duplicateEmail();
            throw ex;
        }
        return deliver(pending);
    }

    @Override
    public InvitationResponse resendStaff(long accountId) { return resend(accountId, RoleType.ADMIN); }

    @Override
    public InvitationResponse resendGuide(long accountId) { return resend(accountId, RoleType.GUIDE); }

    private InvitationResponse resend(long accountId, RoleType expectedRole) {
        Pending pending = transactions.execute(status -> {
            authorization.require(Permission.ACCOUNT_ROLES_WRITE);
            var account = accounts.lockById(accountId).orElseThrow(InvitationServiceImpl::notFound);
            Pending current = pending(account);
            if (expectedRole != current.role()) throw notFound();
            audit(authorization.currentAccountId(), accountId, "INVITATION_RESEND_REQUESTED", current.role());
            return current;
        });
        return deliver(pending);
    }

    private InvitationResponse deliver(Pending pending) {
        // PostgreSQL has committed before Redis/mail; a failed delivery leaves a resumable invitation.
        String code = crypto.otp();
        String hash = otpHash(pending.email(), code);
        String delivery = "sent";
        try {
            if (!store.issue(pending.email(), hash, pending.context())) {
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "INVITATION_RATE_LIMITED", "Request a new activation code later");
            }
            mail.send(pending.email(), code);
        }
        catch (MailException ex) {
            try { store.cancel(pending.email(), hash); }
            catch (DataAccessException unavailable) { log.warn("Unable to cancel undelivered invitation code; accountId={}", pending.accountId()); }
            log.warn("Invitation email delivery failed; accountId={}", pending.accountId());
            delivery = "retry_required";
        } catch (DataAccessException ex) {
            log.warn("Invitation challenge delivery unavailable; accountId={}", pending.accountId());
            delivery = "retry_required";
        }
        return new InvitationResponse(pending.accountId(), pending.email(), pending.role().getValue(),
                true, delivery);
    }

    @Override
    public void activate(InvitationRequests.Activate request) {
        transactions.executeWithoutResult(status -> {
            var account = accounts.lockByEmail(request.email()).orElseThrow(InvitationServiceImpl::invalidOtp);
            Pending pending;
            try { pending = pending(account); }
            catch (AuthException ex) { throw invalidOtp(); }
            if (!store.consume(request.email(), otpHash(request.email(), request.otp()), pending.context())) throw invalidOtp();
            account.setPasswordHash(passwords.encode(request.newPassword()));
            account.setEmailVerifiedAt(OffsetDateTime.now(clock));
            account.setMustChangePassword(false);
            account.setSessionVersion(Math.addExact(account.getSessionVersion(), 1));
            accounts.flush();
            audit(account.getId(), account.getId(), "INVITATION_ACTIVATED", pending.role());
        });
    }

    private Pending pending(AppAccount account) {
        if (account.getStatus() != AccountStatus.ACTIVE || account.getEmailVerifiedAt() != null
                || !Boolean.TRUE.equals(account.getMustChangePassword())) {
            throw new AuthException(HttpStatus.CONFLICT, "INVITATION_NOT_PENDING", "Account has no pending invitation");
        }
        var grants = roles.findAllByAccountId(account.getId());
        if (grants.size() != 1) throw notFound();
        RoleType role = grants.getFirst().getRole();
        var created = audits.findFirstByEntityTypeAndEntityIdAndActionOrderByIdDesc("invitation", account.getId(), "INVITATION_CREATED")
                .orElseThrow(InvitationServiceImpl::notFound);
        var details = json.readTree(created.getDetails());
        if (details.path("invitationVersion").asInt() != 2 || !role.getValue().equals(details.path("role").asText())) throw notFound();
        Long guideId = null;
        if (role == RoleType.GUIDE) {
            var guide = guides.findByAccountIdAndActiveTrue(account.getId()).orElseThrow(InvitationServiceImpl::notFound);
            guideId = guide.getId();
        } else if (role != RoleType.ADMIN) throw notFound();
        return snapshot(account, role, guideId);
    }

    private Pending snapshot(AppAccount account, RoleType role, Long guideId) {
        String context = crypto.hmac("invitation:activate:v3:" + account.getId() + ":" + account.getEmail() + ":"
                + crypto.sessionFingerprint(account.getPasswordHash(), account.getSessionVersion()) + ":"
                + account.getAuthorizationVersion() + ":" + role + ":" + guideId);
        return new Pending(account.getId(), account.getEmail(), role, context);
    }

    private String otpHash(String email, String code) { return crypto.hmac("invitation:activate:otp:" + email + ":" + code); }

    private void audit(long actorId, long accountId, String action, RoleType role) {
        var details = new LinkedHashMap<String, Object>();
        details.put("role", role.getValue());
        details.put("invitationVersion", 2);
        var audit = new AuditLog();
        audit.setActorId(actorId);
        audit.setEntityType("invitation");
        audit.setEntityId(accountId);
        audit.setAction(action);
        audit.setDetails(json.writeValueAsString(details));
        audit.setOccurredAt(OffsetDateTime.now(clock));
        audits.saveAndFlush(audit);
    }

    private record Pending(long accountId, String email, RoleType role, String context) {}

    private static AuthException duplicateEmail() {
        return new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Invitation resource was not found");
    }

    private static AuthException invalidOtp() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Activation code is invalid or expired");
    }
}
