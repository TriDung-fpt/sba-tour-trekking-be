package com.trekking.app.features.company.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.entity.AccountRole;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.company.dto.request.CompanyDecisionRequest;
import com.trekking.app.features.company.dto.request.CompanyProfileRequest;
import com.trekking.app.features.company.dto.response.CompanyLicenseResponse;
import com.trekking.app.features.company.dto.response.CompanyPageResponse;
import com.trekking.app.features.company.dto.response.CompanyResponse;
import com.trekking.app.features.company.entity.Company;
import com.trekking.app.features.company.entity.CompanyMember;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.company.repository.CompanyMemberRepository;
import com.trekking.app.features.company.repository.CompanyRepository;
import com.trekking.app.features.company.service.CompanyLicenseStorageService;
import com.trekking.app.features.company.service.CompanyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class CompanyServiceImpl implements CompanyService {
    private final AuthorizationService authorization;
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final AuditLogRepository audits;
    private final CompanyLicenseStorageService licenses;
    private final ObjectMapper json;
    private final Clock clock;
    private final EntityManager entityManager;

    public CompanyServiceImpl(AuthorizationService authorization, CompanyRepository companies,
            CompanyMemberRepository members, AppAccountRepository accounts, AccountRoleRepository roles,
            AuditLogRepository audits, CompanyLicenseStorageService licenses, ObjectMapper json, Clock clock,
            EntityManager entityManager) {
        this.authorization = authorization;
        this.companies = companies;
        this.members = members;
        this.accounts = accounts;
        this.roles = roles;
        this.audits = audits;
        this.licenses = licenses;
        this.json = json;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public CompanyResponse create(CompanyProfileRequest request) {
        long actor = authorization.currentAccountId();
        // Serialize role additions against account administration and concurrent company creation.
        var account = accounts.lockById(actor).orElseThrow(AuthException::invalidSession);
        entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        if (!authorization.currentRoles().contains(RoleType.CUSTOMER)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "A customer account is required to register a company");
        }
        var company = new Company();
        applyProfile(company, request);
        saveProfile(company);
        var owner = new CompanyMember();
        owner.setCompanyId(company.getId());
        owner.setAccountId(actor);
        owner.setMemberRole(CompanyMemberRole.OWNER);
        members.save(owner);
        if (roles.findAllByAccountId(actor).stream().noneMatch(role -> role.getRole() == RoleType.COMPANY_MANAGER)) {
            var role = new AccountRole();
            role.setAccountId(actor);
            role.setRole(RoleType.COMPANY_MANAGER);
            roles.save(role);
        }
        audit(actor, company, "COMPANY_CREATED", "Company profile submitted", Map.of());
        return response(company);
    }

    @Override
    public CompanyPageResponse list(int page, int size) {
        long actor = authorization.currentAccountId();
        var paging = paging(page, size);
        if (!authorization.currentRoles().contains(RoleType.COMPANY_MANAGER)) {
            return new CompanyPageResponse(java.util.List.of(), page, size, 0, 0);
        }
        return page(companies.findManagedBy(actor, paging));
    }

    @Override
    public CompanyResponse read(long companyId) {
        authorization.requireCompany(Permission.COMPANY_READ, companyId);
        return response(find(companyId));
    }

    @Override
    @Transactional
    public CompanyResponse update(long companyId, CompanyProfileRequest request) {
        authorization.requireCompany(Permission.COMPANY_MANAGE, companyId);
        var company = locked(companyId);
        authorization.requireCompany(Permission.COMPANY_MANAGE, companyId);
        ensureEditable(company);
        var before = snapshot(company);
        applyProfile(company, request);
        invalidateApproval(company);
        saveProfile(company);
        audit(authorization.currentAccountId(), company, "COMPANY_UPDATED", "Company profile updated", before);
        return response(company);
    }

    @Override
    @Transactional
    public CompanyResponse resubmit(long companyId) {
        authorization.requireCompany(Permission.COMPANY_MANAGE, companyId);
        var company = locked(companyId);
        authorization.requireCompany(Permission.COMPANY_MANAGE, companyId);
        if (company.getStatus() != CompanyStatus.REJECTED) throw stateConflict();
        var before = snapshot(company);
        clearDecision(company);
        company.setStatus(CompanyStatus.PENDING);
        companies.flush();
        audit(authorization.currentAccountId(), company, "COMPANY_RESUBMITTED", "Company profile resubmitted", before);
        return response(company);
    }

    @Override
    public CompanyPageResponse reviewList(String status, int page, int size) {
        authorization.require(Permission.COMPANY_REVIEW);
        var paging = paging(page, size);
        if (status == null || status.isBlank()) return page(companies.findAll(paging), true);
        var parsed = Arrays.stream(CompanyStatus.values()).filter(value -> value.getValue().equals(status))
                .findFirst().orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Unknown company status"));
        return page(companies.findByStatus(parsed, paging), true);
    }

    @Override
    public CompanyResponse reviewRead(long companyId) {
        authorization.require(Permission.COMPANY_REVIEW);
        return response(find(companyId), true);
    }

    @Override
    @Transactional
    public CompanyResponse decide(long companyId, CompanyDecisionRequest request) {
        authorization.require(Permission.COMPANY_REVIEW);
        var company = locked(companyId);
        authorization.require(Permission.COMPANY_REVIEW);
        var before = snapshot(company);
        CompanyStatus next;
        switch (request.decision()) {
            case "approve" -> {
                if (company.getStatus() != CompanyStatus.PENDING) throw stateConflict();
                // Existing arbitrary URLs and missing local evidence cannot be approved.
                if (company.getLicenseFileUrl() == null) {
                    throw new AuthException(HttpStatus.CONFLICT, "COMPANY_LICENSE_REQUIRED", "Upload company license evidence before approval");
                }
                licenses.read(company.getLicenseFileUrl());
                next = CompanyStatus.APPROVED;
            }
            case "reject" -> {
                if (company.getStatus() != CompanyStatus.PENDING) throw stateConflict();
                next = CompanyStatus.REJECTED;
            }
            case "suspend" -> {
                if (company.getStatus() != CompanyStatus.APPROVED) throw stateConflict();
                next = CompanyStatus.SUSPENDED;
            }
            default -> throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Unknown company decision");
        }
        long actor = authorization.currentAccountId();
        company.setStatus(next);
        company.setApprovedBy(actor);
        company.setApprovedAt(OffsetDateTime.now(clock));
        company.setDecisionReason(request.reason().trim());
        companies.flush();
        audit(actor, company, "COMPANY_" + next.name(), request.reason().trim(), before);
        return response(company, true);
    }

    @Override
    @Transactional
    public CompanyResponse uploadLicense(long companyId, MultipartFile file) {
        authorization.requireCompany(Permission.COMPANY_MANAGE, companyId);
        var company = locked(companyId);
        authorization.requireCompany(Permission.COMPANY_MANAGE, companyId);
        ensureEditable(company);
        var before = snapshot(company);
        String oldReference = company.getLicenseFileUrl();
        String reference = licenses.store(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) licenses.delete(oldReference);
                else licenses.delete(reference);
            }
        });
        company.setLicenseFileUrl(reference);
        invalidateApproval(company);
        companies.flush();
        audit(authorization.currentAccountId(), company, "COMPANY_LICENSE_UPLOADED", "Company license uploaded", before);
        return response(company);
    }

    @Override
    public CompanyLicenseResponse license(long companyId, boolean review) {
        if (review) authorization.require(Permission.COMPANY_REVIEW);
        else authorization.requireCompany(Permission.COMPANY_READ, companyId);
        return licenses.read(find(companyId).getLicenseFileUrl());
    }

    private void applyProfile(Company company, CompanyProfileRequest request) {
        String taxCode = request.taxCode().trim().toUpperCase(Locale.ROOT);
        if (companies.existsByTaxCodeAndIdNot(taxCode, company.getId() == null ? 0L : company.getId())) {
            throw new AuthException(HttpStatus.CONFLICT, "COMPANY_TAX_CODE_EXISTS", "Tax code is already registered");
        }
        company.setName(request.name().trim());
        company.setTaxCode(taxCode);
        company.setRepresentativeName(request.representativeName().trim());
        company.setContactEmail(request.contactEmail().trim().toLowerCase(Locale.ROOT));
        company.setContactPhone(request.contactPhone().trim());
        company.setAddress(request.address().trim());
        company.setDescription(request.description() == null ? null : request.description().trim());
    }

    private void saveProfile(Company company) {
        try {
            companies.saveAndFlush(company);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMostSpecificCause().getMessage().contains("uq_company_tax_code")) {
                throw new AuthException(HttpStatus.CONFLICT, "COMPANY_TAX_CODE_EXISTS", "Tax code is already registered");
            }
            throw ex;
        }
    }

    private void ensureEditable(Company company) {
        if (company.getStatus() == CompanyStatus.SUSPENDED) throw stateConflict();
    }

    private void invalidateApproval(Company company) {
        if (company.getStatus() == CompanyStatus.APPROVED) {
            clearDecision(company);
            company.setStatus(CompanyStatus.PENDING);
        }
    }

    private void clearDecision(Company company) {
        company.setApprovedAt(null);
        company.setApprovedBy(null);
        company.setDecisionReason(null);
    }

    private Company find(long companyId) {
        return companies.findById(companyId).orElseThrow(CompanyServiceImpl::missing);
    }

    private Company locked(long companyId) {
        var company = companies.findLockedById(companyId).orElseThrow(CompanyServiceImpl::missing);
        // A previous scope guard may already have loaded the row before waiting for this lock.
        entityManager.refresh(company, LockModeType.PESSIMISTIC_WRITE);
        return company;
    }

    private PageRequest paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Page must be nonnegative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }

    private CompanyPageResponse page(Page<Company> page) {
        return page(page, false);
    }

    private CompanyPageResponse page(Page<Company> page, boolean review) {
        return new CompanyPageResponse(page.getContent().stream().map(company -> response(company, review)).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private CompanyResponse response(Company company) {
        return response(company, false);
    }

    private CompanyResponse response(Company company, boolean review) {
        boolean hasLicense = company.getLicenseFileUrl() != null;
        return new CompanyResponse(company.getId(), company.getName(), company.getTaxCode(),
                company.getRepresentativeName(), company.getContactEmail(), company.getContactPhone(),
                company.getAddress(), company.getDescription(), company.getStatus().getValue(), hasLicense,
                hasLicense ? "/api/v1/" + (review ? "admin/" : "") + "companies/" + company.getId() + "/license" : null,
                company.getApprovedBy(), company.getApprovedAt(), company.getDecisionReason(),
                company.getCreatedAt(), company.getUpdatedAt());
    }

    private Map<String, Object> snapshot(Company company) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("status", company.getStatus().getValue());
        fields.put("name", company.getName());
        fields.put("taxCode", company.getTaxCode());
        fields.put("representativeName", company.getRepresentativeName());
        fields.put("contactEmail", company.getContactEmail());
        fields.put("contactPhone", company.getContactPhone());
        fields.put("address", company.getAddress());
        fields.put("description", company.getDescription());
        fields.put("licenseAvailable", company.getLicenseFileUrl() != null);
        fields.put("approvedBy", company.getApprovedBy());
        fields.put("approvedAt", company.getApprovedAt());
        fields.put("decisionReason", company.getDecisionReason());
        return fields;
    }

    private void audit(long actor, Company company, String action, String reason, Map<String, Object> before) {
        var audit = new AuditLog();
        audit.setActorId(actor);
        audit.setEntityType("company");
        audit.setEntityId(company.getId());
        audit.setAction(action);
        audit.setReason(reason);
        audit.setDetails(json.writeValueAsString(Map.of("before", before, "after", snapshot(company))));
        audit.setOccurredAt(OffsetDateTime.now(clock));
        audits.saveAndFlush(audit);
    }

    private static AuthException stateConflict() {
        return new AuthException(HttpStatus.CONFLICT, "COMPANY_STATE_CONFLICT", "Company state does not allow this action");
    }

    private static AuthException missing() {
        return new AuthException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company was not found");
    }
}
