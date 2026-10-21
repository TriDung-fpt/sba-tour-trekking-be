package com.trekking.app.features.statement.service.impl;

import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.statement.dto.response.PartnerStatementPage;
import com.trekking.app.features.statement.repository.CompanyStatementItemRepository;
import com.trekking.app.features.statement.repository.CompanyStatementRepository;
import com.trekking.app.features.statement.repository.CompanyTransferRepository;
import com.trekking.app.features.statement.service.PartnerStatementService;
import com.trekking.app.features.tour.repository.TourCompanyMemberRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PartnerStatementServiceImpl implements PartnerStatementService {
    private final TourCompanyMemberRepository members;
    private final CompanyStatementRepository statements;
    private final CompanyStatementItemRepository items;
    private final CompanyTransferRepository transfers;

    public PartnerStatementServiceImpl(TourCompanyMemberRepository members, CompanyStatementRepository statements,
            CompanyStatementItemRepository items, CompanyTransferRepository transfers) {
        this.members = members; this.statements = statements; this.items = items; this.transfers = transfers;
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerStatementPage list(long accountId, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        var companies = members.findAllByAccountIdAndActiveTrueAndMemberRoleInAndCompany_StatusOrderByCompanyIdAsc(
                accountId, List.of(CompanyMemberRole.OWNER, CompanyMemberRole.MANAGER), CompanyStatus.APPROVED)
                .stream().map(m -> m.getCompanyId()).toList();
        if (companies.isEmpty()) return new PartnerStatementPage(List.of(), page, size, 0, 0);
        var result = statements.findAllByCompanyIdIn(companies,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "periodEnd")));
        var response = result.getContent().stream().map(s -> {
            long payable = items.payableTotal(s.getId());
            var transferList = transfers.findAllByStatementIdOrderByTransferredAtAscIdAsc(s.getId()).stream()
                    .map(t -> new PartnerStatementPage.Transfer(t.getAmountVnd(), t.getTransferredAt())).toList();
            long transferred = transferList.stream().mapToLong(PartnerStatementPage.Transfer::amountVnd).sum();
            return new PartnerStatementPage.Item(s.getId(), s.getPeriodStart(), s.getPeriodEnd(), s.getStatus().getValue(),
                    payable, transferred, payable - transferred, transferList, s.getNote());
        }).toList();
        return new PartnerStatementPage(response, page, size, result.getTotalElements(), result.getTotalPages());
    }
}
