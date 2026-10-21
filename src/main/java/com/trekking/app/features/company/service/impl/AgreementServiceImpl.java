package com.trekking.app.features.company.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.company.dto.request.AgreementAcceptRequest;
import com.trekking.app.features.company.dto.request.AgreementOtpRequest;
import com.trekking.app.features.company.dto.response.AgreementOtpResponse;
import com.trekking.app.features.company.dto.response.AgreementReceiptResponse;
import com.trekking.app.features.company.entity.CompanyAgreement;
import com.trekking.app.features.company.entity.CompanyMember;
import com.trekking.app.features.company.entity.CompanyMemberId;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.company.enums.PolicyKind;
import com.trekking.app.features.company.repository.CompanyAgreementRepository;
import com.trekking.app.features.company.repository.CompanyRepository;
import com.trekking.app.features.company.repository.PolicyRepository;
import com.trekking.app.features.company.repository.PolicyVersionRepository;
import com.trekking.app.features.company.service.AgreementChallengeService;
import com.trekking.app.features.company.service.AgreementMailService;
import com.trekking.app.features.company.service.AgreementService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AgreementServiceImpl implements AgreementService {
    private final AuthorizationService authorization;
    private final AppAccountRepository accounts;
    private final CompanyRepository companies;
    private final CompanyAgreementRepository agreements;
    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final EntityManager entities;
    private final AgreementChallengeService challenges;
    private final AgreementMailService mail;
    private final AuthCrypto crypto;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public AgreementServiceImpl(AuthorizationService authorization, AppAccountRepository accounts,
                                CompanyRepository companies, CompanyAgreementRepository agreements,
                                PolicyRepository policies, PolicyVersionRepository versions,
                                EntityManager entities, AgreementChallengeService challenges,
                                AgreementMailService mail, AuthCrypto crypto, PlatformTransactionManager manager, Clock clock) {
        this.authorization = authorization;
        this.accounts = accounts;
        this.companies = companies;
        this.agreements = agreements;
        this.policies = policies;
        this.versions = versions;
        this.entities = entities;
        this.challenges = challenges;
        this.mail = mail;
        this.crypto = crypto;
        this.transactions = new TransactionTemplate(manager);
        this.clock = clock;
    }

    @Override
    public AgreementOtpResponse requestOtp(long companyId, AgreementOtpRequest request) {
        var context = transactions.execute(status -> context(companyId, request.policyVersionId(), false));
        String otp = crypto.otp();
        challenges.issue(context.accountId(), companyId, context.email(), context.fingerprint(), otp);
        try {
            // No database transaction is held across SMTP or Redis calls.
            mail.send(context.email(), otp, companyId, request.policyVersionId());
        } catch (MailException ex) {
            challenges.cancel(context.accountId(), companyId, context.fingerprint());
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "Agreement code could not be delivered; request a new code after the cooldown");
        }
        return new AgreementOtpResponse(300, 60);
    }

    @Override
    public AgreementReceiptResponse accept(long companyId, AgreementAcceptRequest request, String peerIp) {
        var prepared = transactions.execute(status -> context(companyId, request.policyVersionId(), false));
        // Atomic consume is deliberately outside PostgreSQL: rollback must not restore a used code.
        challenges.consume(prepared.accountId(), companyId, prepared.fingerprint(), request.otp());
        return transactions.execute(status -> {
            var current = context(companyId, request.policyVersionId(), true);
            if (!crypto.same(current.fingerprint(), prepared.fingerprint())) {
                throw conflict("AGREEMENT_CONTEXT_CHANGED", "Signing context changed; request a new agreement code");
            }
            var now = OffsetDateTime.now(clock);
            var agreement = new CompanyAgreement();
            agreement.setCompanyId(companyId);
            agreement.setPolicyVersionId(request.policyVersionId());
            agreement.setAcceptedBy(current.accountId());
            agreement.setAcceptedAt(now);
            agreement.setEmailVerifiedAt(now);
            agreement.setAcceptedIp(peerAddress(peerIp));
            agreement.setPlatformFeePercent(current.fee());
            agreement.setPayoutWaitHours(current.waitHours());
            agreement.setPayoutTermsSnapshot(current.terms());
            agreements.saveAndFlush(agreement);
            return receiptResponse(agreement);
        });
    }

    @Override
    public List<AgreementReceiptResponse> receipts(long companyId) {
        return transactions.execute(status -> {
            authorization.requireCompany(Permission.COMPANY_READ, companyId);
            return agreements.findByCompanyIdOrderByAcceptedAtDescIdDesc(companyId).stream().map(this::receiptResponse).toList();
        });
    }

    @Override
    public AgreementReceiptResponse receipt(long companyId, long agreementId) {
        return transactions.execute(status -> {
            authorization.requireCompany(Permission.COMPANY_READ, companyId);
            return receiptResponse(agreements.findByIdAndCompanyId(agreementId, companyId).orElseThrow(AgreementServiceImpl::notFound));
        });
    }

    private SigningContext context(long companyId, long versionId, boolean lock) {
        long actor = authorization.currentAccountId();
        var account = (lock ? accounts.lockById(actor) : accounts.findById(actor)).orElseThrow(AuthException::invalidSession);
        if (lock) entities.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        var company = (lock ? companies.findLockedById(companyId) : companies.findById(companyId))
                .orElseThrow(AgreementServiceImpl::notFound);
        if (lock) entities.refresh(company, LockModeType.PESSIMISTIC_WRITE);
        var memberId = new CompanyMemberId(companyId, actor);
        var member = lock ? entities.find(CompanyMember.class, memberId, LockModeType.PESSIMISTIC_WRITE)
                : entities.find(CompanyMember.class, memberId);
        if (lock && member != null) entities.refresh(member, LockModeType.PESSIMISTIC_WRITE);
        authorization.requireCompany(Permission.COMPANY_MANAGE, companyId);
        if (member == null || !Boolean.TRUE.equals(member.getActive()) || member.getMemberRole() != CompanyMemberRole.OWNER) {
            throw new AuthException(HttpStatus.FORBIDDEN, "AGREEMENT_OWNER_REQUIRED", "Only an active company owner may accept cooperation terms");
        }
        if (company.getStatus() != CompanyStatus.APPROVED) {
            throw conflict("COMPANY_NOT_APPROVED", "Company must be approved before accepting cooperation terms");
        }
        var version = versions.findById(versionId).orElseThrow(AgreementServiceImpl::notFound);
        var policy = (lock ? policies.findLockedById(version.getPolicyId()) : policies.findById(version.getPolicyId()))
                .orElseThrow(AgreementServiceImpl::notFound);
        if (lock) {
            entities.refresh(policy, LockModeType.PESSIMISTIC_WRITE);
            version = versions.findLockedById(versionId).orElseThrow(AgreementServiceImpl::notFound);
            entities.refresh(version, LockModeType.PESSIMISTIC_WRITE);
        }
        var now = OffsetDateTime.now(clock);
        if (policy.getKind() != PolicyKind.COOPERATION || !Boolean.TRUE.equals(policy.getActive())
                || (policy.getCompanyId() != null && policy.getCompanyId() != companyId)) throw notFound();
        var latest = versions.findFirstByPolicyIdAndApprovedByIsNotNullAndApprovedAtLessThanEqualAndEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionNumberDesc(
                policy.getId(), now, now);
        if (latest.isEmpty() || latest.get().getId() != versionId || !PolicyServiceImpl.hasTerms(version)) {
            throw conflict("POLICY_VERSION_INELIGIBLE", "Policy version is not the current approved effective version with explicit financial terms");
        }
        if (agreements.existsByCompanyIdAndPolicyVersionId(companyId, versionId)) {
            throw conflict("AGREEMENT_ALREADY_ACCEPTED", "This company has already accepted the policy version");
        }
        String evidence = actor + "|" + account.getEmail() + "|" + account.getEmailVerifiedAt() + "|" + account.getSessionVersion()
                + "|" + account.getAuthorizationVersion()
                + "|" + companyId + "|" + company.getStatus() + "|" + company.getApprovedAt() + "|" + company.getUpdatedAt()
                + "|" + member.getMemberRole() + "|" + member.getUpdatedAt() + "|" + policy.getId() + "|" + policy.getCompanyId()
                + "|" + policy.getKind() + "|" + policy.getActive() + "|" + versionId + "|" + version.getContent()
                + "|" + version.getApprovedBy() + "|" + version.getApprovedAt() + "|" + version.getEffectiveFrom()
                + "|" + version.getPlatformFeePercent() + "|" + version.getPayoutWaitHours() + "|" + version.getPayoutTermsSnapshot();
        return new SigningContext(actor, account.getEmail(), crypto.hmac(evidence), version.getPlatformFeePercent(),
                version.getPayoutWaitHours(), version.getPayoutTermsSnapshot());
    }

    private AgreementReceiptResponse receiptResponse(CompanyAgreement agreement) {
        var version = versions.findById(agreement.getPolicyVersionId()).orElseThrow(AgreementServiceImpl::notFound);
        return new AgreementReceiptResponse(agreement.getId(), agreement.getCompanyId(), agreement.getPolicyVersionId(),
                agreement.getAcceptedBy(), agreement.getAcceptedAt(), agreement.getAcceptedIp() == null ? null : agreement.getAcceptedIp().getHostAddress(),
                agreement.getEmailVerifiedAt(), agreement.getPlatformFeePercent(), agreement.getPayoutWaitHours(),
                agreement.getPayoutTermsSnapshot(), version.getVersionNumber(), version.getContent());
    }

    private static InetAddress peerAddress(String address) {
        try {
            if (address == null || !address.matches("[0-9a-fA-F:.]+")) throw new UnknownHostException();
            return InetAddress.getByName(address);
        } catch (UnknownHostException ex) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PEER_ADDRESS", "Request peer address is invalid; request a new agreement code");
        }
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private record SigningContext(long accountId, String email, String fingerprint,
                                  java.math.BigDecimal fee, int waitHours, String terms) {}
}
