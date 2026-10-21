package com.trekking.app.features.statement.service.impl;

import com.trekking.app.core.common.idempotency.AuditedIdempotency;
import com.trekking.app.core.common.validation.EvidenceUrl;
import com.trekking.app.features.company.entity.Company;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.statement.dto.request.ApproveStatementRequest;
import com.trekking.app.features.statement.dto.request.CreateStatementRequest;
import com.trekking.app.features.statement.dto.request.RecordTransferRequest;
import com.trekking.app.features.statement.dto.response.AdminCompanyPage;
import com.trekking.app.features.statement.dto.response.AdminStatementPage;
import com.trekking.app.features.statement.dto.response.StatementOperationResponse;
import com.trekking.app.features.statement.entity.CompanyStatement;
import com.trekking.app.features.statement.entity.CompanyStatementItem;
import com.trekking.app.features.statement.entity.CompanyTransfer;
import com.trekking.app.features.statement.enums.ItemKind;
import com.trekking.app.features.statement.enums.StatementStatus;
import com.trekking.app.features.statement.repository.CompanyStatementItemRepository;
import com.trekking.app.features.statement.repository.CompanyStatementRepository;
import com.trekking.app.features.statement.repository.CompanyTransferRepository;
import com.trekking.app.features.statement.service.FinanceStatementOperationsService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinanceStatementOperationsServiceImpl implements FinanceStatementOperationsService {
    private static final String ELIGIBLE_CTE = """
        with money as (
          select booking_id,
            coalesce(sum(amount_vnd) filter (where allocation_status='accepted'),0) accepted_vnd,
            coalesce(sum(amount_vnd) filter (where allocation_status='pending_review'),0) pending_vnd
          from trekking.customer_payments group by booking_id
        ), refunded as (
          select booking_id, coalesce(sum(amount_vnd),0) refunded_vnd
          from trekking.customer_refunds group by booking_id
        ), held_refunds as (
          select r.booking_id, coalesce(sum(greatest(r.approved_amount_vnd-coalesce(x.refunded_vnd,0),0)),0) held_vnd
          from trekking.refund_requests r
          left join (select refund_request_id, sum(amount_vnd) refunded_vnd from trekking.customer_refunds group by refund_request_id) x
            on x.refund_request_id=r.id
          where r.status in ('approved','processing') group by r.booking_id
        ), eligible as (
          select b.id,b.booking_code,b.company_id,b.total_price_vnd,b.platform_fee_percent,
            coalesce(m.accepted_vnd,0) accepted_vnd,coalesce(m.pending_vnd,0) pending_vnd,
            coalesce(r.refunded_vnd,0) refunded_vnd,coalesce(h.held_vnd,0) held_vnd
          from trekking.bookings b join trekking.tour_schedules s on s.id=b.schedule_id and s.company_id=b.company_id
          left join money m on m.booking_id=b.id left join refunded r on r.booking_id=b.id
          left join held_refunds h on h.booking_id=b.id
          where b.status in ('completed','no_show') and s.status='completed'
            and s.actual_completed_at <= :cutoff
            and (s.actual_completed_at at time zone 'Asia/Ho_Chi_Minh')::date between :periodStart and :periodEnd
            and coalesce(m.accepted_vnd,0) >= b.total_price_vnd and coalesce(m.pending_vnd,0)=0
            and not exists (select 1 from trekking.company_statement_items i where i.booking_id=b.id and i.kind='sale')
            and not exists (select 1 from trekking.refund_requests rr where rr.booking_id=b.id and rr.status='requested')
        )
        """;

    private final EntityManager entityManager;
    private final CompanyStatementRepository statements;
    private final CompanyStatementItemRepository items;
    private final CompanyTransferRepository transfers;
    private final AuditedIdempotency idempotency;

    public FinanceStatementOperationsServiceImpl(EntityManager entityManager, CompanyStatementRepository statements,
            CompanyStatementItemRepository items, CompanyTransferRepository transfers,
            AuditedIdempotency idempotency) {
        this.entityManager = entityManager; this.statements = statements; this.items = items;
        this.transfers = transfers; this.idempotency = idempotency;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCompanyPage eligibleCompanies(LocalDate start, LocalDate end, int page, int size) {
        validatePage(page, size); validatePeriod(start, end);
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(72);
        String sql = ELIGIBLE_CTE + "select c.id,c.name,count(e.id) from eligible e join trekking.companies c on c.id=e.company_id "
                + "where c.status='approved' group by c.id,c.name order by c.name,c.id";
        String countSql = ELIGIBLE_CTE + "select count(*) from (select c.id from eligible e join trekking.companies c on c.id=e.company_id "
                + "where c.status='approved' group by c.id) q";
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("cutoff", cutoff).setParameter("periodStart", start).setParameter("periodEnd", end)
                .setFirstResult(page * size).setMaxResults(size).getResultList();
        Number count = (Number) entityManager.createNativeQuery(countSql).setParameter("cutoff", cutoff)
                .setParameter("periodStart", start).setParameter("periodEnd", end).getSingleResult();
        var response = rows.stream().map(r -> new AdminCompanyPage.Item(((Number) r[0]).longValue(),
                (String) r[1], ((Number) r[2]).longValue())).toList();
        long total = count.longValue();
        return new AdminCompanyPage(response, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatementPage list(String rawStatus, Long companyId, int page, int size) {
        validatePage(page, size);
        StatementStatus status = null;
        if (rawStatus != null && !rawStatus.isBlank()) {
            try { status = StatementStatus.valueOf(rawStatus.trim().toUpperCase()); }
            catch (IllegalArgumentException ex) { throw error(HttpStatus.BAD_REQUEST, "STATEMENT_STATUS", "Unsupported statement status"); }
        }
        var result = statements.search(status, companyId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "periodEnd").and(Sort.by("id"))));
        var rows = result.getContent().stream().map(this::statementItem).toList();
        return new AdminStatementPage(rows, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public StatementOperationResponse create(long adminId, String key, CreateStatementRequest request) {
        validatePeriod(request.periodStart(), request.periodEnd());
        var normalized = new CreateStatementRequest(request.companyId(), request.periodStart(), request.periodEnd(),
                request.note() == null ? null : request.note().trim());
        var prior = idempotency.begin("ADMIN_STATEMENT_CREATED", key, normalized);
        if (prior.isPresent()) return operation(statements.findById(prior.get().getEntityId()).orElseThrow());
        lockBusinessKey("statement-company:" + normalized.companyId());
        if (statements.findByCompanyIdAndPeriodStartAndPeriodEnd(normalized.companyId(), normalized.periodStart(), normalized.periodEnd()).isPresent())
            throw error(HttpStatus.CONFLICT, "STATEMENT_PERIOD_EXISTS", "A statement already exists for this company and period");
        Company company = entityManager.find(Company.class, normalized.companyId());
        if (company == null || company.getStatus() != CompanyStatus.APPROVED)
            throw error(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Approved company not found");
        OffsetDateTime cutoff = OffsetDateTime.now();
        OffsetDateTime eligibleCutoff = cutoff.minusHours(72);
        @SuppressWarnings("unchecked") List<Object[]> candidates = entityManager.createNativeQuery(ELIGIBLE_CTE +
                "select id,booking_code,company_id,accepted_vnd,refunded_vnd,held_vnd,platform_fee_percent from eligible where company_id=:companyId order by id")
                .setParameter("cutoff", eligibleCutoff).setParameter("periodStart", normalized.periodStart())
                .setParameter("periodEnd", normalized.periodEnd()).setParameter("companyId", normalized.companyId()).getResultList();
        if (candidates.isEmpty()) throw error(HttpStatus.CONFLICT, "NO_ELIGIBLE_BOOKINGS", "There are no settled bookings for this period");
        CompanyStatement statement = new CompanyStatement();
        statement.setCompanyId(company.getId()); statement.setPeriodStart(normalized.periodStart());
        statement.setPeriodEnd(normalized.periodEnd()); statement.setCutoffAt(cutoff);
        statement.setPreparedBy(adminId); statement.setStatus(StatementStatus.DRAFT); statement.setNote(normalized.note());
        statement = statements.saveAndFlush(statement);
        for (Object[] row : candidates) {
            long accepted = ((Number) row[3]).longValue();
            long refunded = ((Number) row[4]).longValue();
            long heldRequest = ((Number) row[5]).longValue();
            long retained = accepted - refunded;
            if (retained <= 0) continue;
            BigDecimal feePercent = (BigDecimal) row[6];
            long fee = BigDecimal.valueOf(retained).multiply(feePercent)
                    .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).longValue();
            long held = Math.min(heldRequest, Math.max(0, retained - fee));
            CompanyStatementItem item = new CompanyStatementItem();
            item.setStatementId(statement.getId()); item.setCompanyId(company.getId());
            item.setBookingId(((Number) row[0]).longValue()); item.setKind(ItemKind.SALE);
            item.setRetainedRevenueVnd(retained); item.setPlatformFeeVnd(fee); item.setHeldAmountVnd(held);
            items.save(item);
        }
        if (!items.existsByStatementId(statement.getId()))
            throw error(HttpStatus.CONFLICT, "NO_PAYABLE_BOOKINGS", "No positive partner balance is eligible for settlement");
        audit(adminId, "ADMIN_STATEMENT_CREATED", "company_statement", statement.getId(), normalized.note(), key, normalized);
        return operation(statement);
    }

    @Override
    @Transactional
    public StatementOperationResponse approve(long adminId, long statementId, String key, ApproveStatementRequest request) {
        var normalized = new ApproveStatementRequest(request == null || request.note() == null ? null : request.note().trim());
        var prior = idempotency.begin("ADMIN_STATEMENT_APPROVED", key, new Object[]{statementId, normalized});
        if (prior.isPresent()) return operation(statements.findById(prior.get().getEntityId()).orElseThrow());
        CompanyStatement statement = statements.lockById(statementId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "STATEMENT_NOT_FOUND", "Statement not found"));
        if (statement.getStatus() != StatementStatus.DRAFT)
            throw error(HttpStatus.CONFLICT, "STATEMENT_NOT_DRAFT", "Only a draft statement can be approved");
        if (statement.getPreparedBy().equals(adminId))
            throw error(HttpStatus.FORBIDDEN, "STATEMENT_SEPARATION", "A different administrator must approve this statement");
        if (!items.existsByStatementId(statementId) || items.payableTotal(statementId) <= 0)
            throw error(HttpStatus.CONFLICT, "STATEMENT_NOT_PAYABLE", "Statement has no positive payout amount");
        statement.setStatus(StatementStatus.APPROVED); statement.setApprovedBy(adminId);
        statement.setApprovedAt(OffsetDateTime.now());
        if (normalized.note() != null && !normalized.note().isBlank()) statement.setNote(normalized.note());
        statements.save(statement);
        audit(adminId, "ADMIN_STATEMENT_APPROVED", "company_statement", statementId, normalized.note(), key,
                new Object[]{statementId, normalized});
        return operation(statement);
    }

    @Override
    @Transactional
    public StatementOperationResponse recordTransfer(long adminId, long statementId, String key, RecordTransferRequest request) {
        var normalized = new RecordTransferRequest(request.amountVnd(), request.bankReference().trim(),
                request.transferredAt(), EvidenceUrl.require(request.evidenceUrl()));
        var prior = idempotency.begin("ADMIN_TRANSFER_RECORDED", key, new Object[]{statementId, normalized});
        if (prior.isPresent()) {
            CompanyTransfer transfer = transfers.findById(prior.get().getEntityId()).orElseThrow();
            return operation(statements.findById(transfer.getStatementId()).orElseThrow());
        }
        CompanyStatement statement = statements.lockById(statementId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "STATEMENT_NOT_FOUND", "Statement not found"));
        if (statement.getStatus() != StatementStatus.APPROVED && statement.getStatus() != StatementStatus.PAYING)
            throw error(HttpStatus.CONFLICT, "STATEMENT_NOT_APPROVED", "Only an approved statement can have a completed transfer recorded");
        long payable = items.payableTotal(statementId);
        long transferred = transfers.findAllByStatementIdOrderByTransferredAtAscIdAsc(statementId).stream()
                .mapToLong(CompanyTransfer::getAmountVnd).sum();
        if (normalized.amountVnd() > payable - transferred)
            throw error(HttpStatus.CONFLICT, "TRANSFER_AMOUNT_LIMIT", "Transfer exceeds the remaining payable amount");
        CompanyTransfer transfer = new CompanyTransfer();
        transfer.setStatementId(statementId); transfer.setCompanyId(statement.getCompanyId());
        transfer.setAmountVnd(normalized.amountVnd()); transfer.setBankReference(normalized.bankReference());
        transfer.setIdempotencyKey(key); transfer.setTransferredAt(normalized.transferredAt());
        transfer.setRecordedBy(adminId); transfer.setEvidenceUrl(normalized.evidenceUrl());
        transfers.saveAndFlush(transfer);
        transferred += normalized.amountVnd();
        statement.setStatus(transferred >= payable ? StatementStatus.PAID : StatementStatus.PAYING);
        statements.save(statement);
        audit(adminId, "ADMIN_TRANSFER_RECORDED", "company_transfer", transfer.getId(),
                "Evidence recorded for an already executed transfer", key, new Object[]{statementId, normalized});
        return operation(statement);
    }

    private AdminStatementPage.Item statementItem(CompanyStatement s) {
        var lines = items.findAllByStatementIdOrderByIdAsc(s.getId()).stream().map(i ->
                new AdminStatementPage.Line(i.getBookingId(), i.getBooking().getBookingCode(), i.getKind().getValue(),
                        i.getRetainedRevenueVnd(), i.getPlatformFeeVnd(), i.getHeldAmountVnd(), i.getPayableVnd())).toList();
        long payable = items.payableTotal(s.getId());
        var transferRows = transfers.findAllByStatementIdOrderByTransferredAtAscIdAsc(s.getId());
        var transferDtos = transferRows.stream().map(t -> new AdminStatementPage.Transfer(t.getAmountVnd(),
                t.getBankReference(), t.getTransferredAt(), t.getEvidenceUrl())).toList();
        long transferred = transferRows.stream().mapToLong(CompanyTransfer::getAmountVnd).sum();
        return new AdminStatementPage.Item(s.getId(), s.getCompanyId(), s.getCompany().getName(), s.getPeriodStart(),
                s.getPeriodEnd(), s.getCutoffAt(), s.getStatus().getValue(), payable, transferred, payable - transferred,
                s.getNote(), lines, transferDtos);
    }

    private StatementOperationResponse operation(CompanyStatement s) {
        long payable = items.payableTotal(s.getId());
        long transferred = transfers.findAllByStatementIdOrderByTransferredAtAscIdAsc(s.getId()).stream()
                .mapToLong(CompanyTransfer::getAmountVnd).sum();
        return new StatementOperationResponse(s.getId(), s.getStatus().getValue(), payable, transferred, payable - transferred);
    }

    private void audit(long actorId, String action, String type, long id, String reason, String key, Object request) {
        idempotency.record(actorId, action, type, id, reason, key, request);
    }

    private void lockBusinessKey(String key) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
                .setParameter("lockKey", key).getSingleResult();
    }
    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start) || end.isAfter(LocalDate.now()) || start.isBefore(LocalDate.now().minusYears(5)))
            throw error(HttpStatus.BAD_REQUEST, "PERIOD_RANGE", "Statement period must be valid, historical, and no longer than five years ago");
    }
    private void validatePage(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, "PAGINATION", "Invalid pagination");
    }
    private ResponseStatusException error(HttpStatus status, String code, String message) {
        return new ResponseStatusException(status, code + ": " + message);
    }
}
