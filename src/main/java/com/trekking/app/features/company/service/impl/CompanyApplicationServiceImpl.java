package com.trekking.app.features.company.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.account.entity.AccountRole;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.auth.enums.OtpPurpose;
import com.trekking.app.features.auth.repository.RedisAuthStore;
import com.trekking.app.features.company.dto.request.ActivateCompanyApplicationRequest;
import com.trekking.app.features.company.dto.request.CompanyApplicationRequest;
import com.trekking.app.features.company.dto.request.RejectCompanyApplicationRequest;
import com.trekking.app.features.company.dto.response.CompanyApplicationQueueItem;
import com.trekking.app.features.company.dto.response.CompanyApplicationStatusResponse;
import com.trekking.app.features.company.entity.Company;
import com.trekking.app.features.company.entity.CompanyMember;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.company.repository.CompanyAuditLogRepository;
import com.trekking.app.features.company.repository.CompanyMemberRepository;
import com.trekking.app.features.company.repository.CompanyRepository;
import com.trekking.app.features.company.service.CompanyApplicationMailSender;
import com.trekking.app.features.company.service.CompanyApplicationService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CompanyApplicationServiceImpl implements CompanyApplicationService {
    private static final Logger log = LoggerFactory.getLogger(CompanyApplicationServiceImpl.class);
    private static final String SAFETY_RULES_VERSION = "2026-10-22";
    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final CompanyAuditLogRepository auditLogs;
    private final RedisAuthStore tokens;
    private final AuthCrypto crypto;
    private final PasswordEncoder passwords;
    private final CompanyApplicationMailSender mail;
    private final Clock clock;
    private final String frontendUrl;

    public CompanyApplicationServiceImpl(AppAccountRepository accounts, AccountRoleRepository roles,
            CompanyRepository companies, CompanyMemberRepository members, CompanyAuditLogRepository auditLogs,
            RedisAuthStore tokens, AuthCrypto crypto, PasswordEncoder passwords, CompanyApplicationMailSender mail,
            Clock clock, @Value("${app.partner.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.accounts = accounts;
        this.roles = roles;
        this.companies = companies;
        this.members = members;
        this.auditLogs = auditLogs;
        this.tokens = tokens;
        this.crypto = crypto;
        this.passwords = passwords;
        this.mail = mail;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Override
    @Transactional
    public void apply(CompanyApplicationRequest request) {
        if (accounts.findByEmail(request.email()).isPresent()) throw duplicate("Email đã được đăng ký.");
        if (companies.findByTaxCode(request.taxCode()).isPresent()) throw duplicate("Mã số thuế đã được đăng ký.");
        AppAccount account = new AppAccount();
        account.setEmail(request.email());
        account.setFullName(request.contactName());
        account.setPhone(request.phone());
        account.setPasswordHash(passwords.encode(crypto.token()));
        account.setStatus(AccountStatus.ACTIVE);
        account = accounts.saveAndFlush(account);
        AccountRole customerRole = new AccountRole();
        customerRole.setAccountId(account.getId());
        customerRole.setRole(RoleType.CUSTOMER);
        roles.save(customerRole);

        Company company = new Company();
        company.setName(request.businessName());
        company.setTaxCode(request.taxCode());
        company.setBusinessType(request.businessType());
        company.setFoundedYear(request.foundedYear());
        company.setRepresentativeName(request.contactName());
        company.setContactEmail(request.email());
        company.setContactPhone(request.phone());
        company.setAddress(request.address());
        company.setDescription(request.description());
        company.setTourRegions(request.tourRegions());
        company.setSafetyRulesAcceptedAt(OffsetDateTime.now(clock));
        company.setSafetyRulesVersion(SAFETY_RULES_VERSION);
        company.setStatus(CompanyStatus.PENDING);
        company = companies.saveAndFlush(company);

        CompanyMember member = new CompanyMember();
        member.setCompanyId(company.getId());
        member.setAccountId(account.getId());
        member.setMemberRole(CompanyMemberRole.OWNER);
        members.save(member);
        sendActivation(account.getId(), request.email(), true);
    }

    @Override
    @Transactional
    public void resendActivation(String email) {
        if (!tokens.reserveEmail(email, OtpPurpose.PARTNER_ACTIVATION)) return;
        accounts.findByEmail(email).filter(a -> a.getEmailVerifiedAt() == null)
                .ifPresent(a -> members.findFirstByAccountIdAndActiveTrueOrderByCreatedAtDesc(a.getId())
                        .filter(member -> companies.findById(member.getCompanyId())
                                .map(company -> company.getStatus() == CompanyStatus.PENDING).orElse(false))
                        .ifPresent(member -> sendActivation(a.getId(), a.getEmail(), false)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUnverifiedPendingApplication(long accountId) {
        return members.findFirstByAccountIdAndActiveTrueOrderByCreatedAtDesc(accountId)
                .flatMap(member -> companies.findById(member.getCompanyId()))
                .map(company -> company.getStatus() == CompanyStatus.PENDING)
                .orElse(false);
    }

    private void sendActivation(long accountId, String email, boolean reportMailFailure) {
        String token = crypto.token();
        tokens.putPartnerActivation(crypto.digest(token), accountId);
        String url = UriComponentsBuilder.fromUriString(frontendUrl).path("/")
                .queryParam("partnerActivation", token).build().toUriString();
        try { mail.sendActivation(email, url); }
        catch (MailException ex) {
            if (!reportMailFailure) {
                log.warn("Partner activation email delivery failed during resend");
                return;
            }
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE",
                    "Không gửi được email kích hoạt. Vui lòng yêu cầu gửi lại liên kết sau.");
        }
    }

    @Override
    @Transactional
    public void activate(ActivateCompanyApplicationRequest request) {
        Long id = tokens.consumePartnerActivation(crypto.digest(request.token()));
        if (id == null) throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_ACTIVATION_LINK", "Liên kết không hợp lệ hoặc đã hết hạn.");
        AppAccount account = accounts.findById(id).orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST,
                "INVALID_ACTIVATION_LINK", "Liên kết không hợp lệ hoặc đã hết hạn."));
        if (account.getEmailVerifiedAt() != null || members.findFirstByAccountIdAndActiveTrueOrderByCreatedAtDesc(id).isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_ACTIVATION_LINK", "Liên kết không hợp lệ hoặc đã được sử dụng.");
        }
        account.setPasswordHash(passwords.encode(request.password()));
        account.setEmailVerifiedAt(OffsetDateTime.now(clock));
        accounts.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyApplicationStatusResponse status(long accountId) {
        return members.findFirstByAccountIdAndActiveTrueOrderByCreatedAtDesc(accountId)
                .flatMap(member -> companies.findById(member.getCompanyId()))
                .map(this::status)
                .orElse(new CompanyApplicationStatusResponse("none", null, null, null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyApplicationQueueItem> pendingApplications() {
        return companies.findAllByStatusOrderByCreatedAtAsc(CompanyStatus.PENDING).stream().map(company -> {
            var member = members.findFirstByCompanyIdAndActiveTrue(company.getId()).orElseThrow();
            var account = accounts.findById(member.getAccountId()).orElseThrow();
            if (account.getEmailVerifiedAt() == null) return null;
            return new CompanyApplicationQueueItem(company.getId(), account.getId(), company.getName(), company.getTaxCode(),
                    company.getBusinessType(), company.getFoundedYear(), company.getAddress(), company.getRepresentativeName(),
                    company.getContactEmail(), company.getContactPhone(), company.getDescription(), company.getTourRegions(),
                    company.getSafetyRulesAcceptedAt(), company.getCreatedAt());
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    @Transactional
    public CompanyApplicationStatusResponse approve(long actorId, long companyId) {
        Company company = pendingCompany(companyId);
        company.setStatus(CompanyStatus.APPROVED);
        company.setApprovedBy(actorId);
        company.setApprovedAt(OffsetDateTime.now(clock));
        company.setDecisionReason(null);
        CompanyMember member = members.findFirstByCompanyIdAndActiveTrue(companyId).orElseThrow();
        if (!roles.existsByAccountIdAndRole(member.getAccountId(), RoleType.COMPANY_MANAGER)) {
            AccountRole partnerRole = new AccountRole();
            partnerRole.setAccountId(member.getAccountId());
            partnerRole.setRole(RoleType.COMPANY_MANAGER);
            roles.save(partnerRole);
        }
        audit(actorId, companyId, "PARTNER_APPLICATION_APPROVED", null);
        notifyDecision(company.getContactEmail(), CompanyStatus.APPROVED, null);
        return status(company);
    }

    @Override
    @Transactional
    public CompanyApplicationStatusResponse reject(long actorId, long companyId, RejectCompanyApplicationRequest request) {
        Company company = pendingCompany(companyId);
        company.setStatus(CompanyStatus.REJECTED);
        company.setDecisionReason(request.reason());
        audit(actorId, companyId, "PARTNER_APPLICATION_REJECTED", request.reason());
        notifyDecision(company.getContactEmail(), CompanyStatus.REJECTED, request.reason());
        return status(company);
    }

    private Company pendingCompany(long id) {
        Company company = companies.findById(id).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND,
                "APPLICATION_NOT_FOUND", "Partner application was not found"));
        if (company.getStatus() != CompanyStatus.PENDING) throw new AuthException(HttpStatus.CONFLICT,
                "APPLICATION_ALREADY_DECIDED", "Partner application was already decided");
        return company;
    }

    private void notifyDecision(String email, CompanyStatus status, String reason) {
        try { mail.sendDecision(email, status, reason); }
        catch (MailException ex) { log.warn("Partner decision email failed; companyId status={}", status); }
    }

    private void audit(long actorId, long companyId, String action, String reason) {
        AuditLog logEntry = new AuditLog();
        logEntry.setActorId(actorId);
        logEntry.setAction(action);
        logEntry.setEntityType("company");
        logEntry.setEntityId(companyId);
        logEntry.setReason(reason);
        logEntry.setDetails("{}");
        logEntry.setOccurredAt(OffsetDateTime.now(clock));
        auditLogs.save(logEntry);
    }

    private CompanyApplicationStatusResponse status(Company company) {
        return new CompanyApplicationStatusResponse(company.getStatus().getValue(), company.getId(),
                company.getName(), company.getDecisionReason());
    }

    private AuthException duplicate(String message) {
        return new AuthException(HttpStatus.CONFLICT, "PARTNER_APPLICATION_EXISTS", message);
    }
}
