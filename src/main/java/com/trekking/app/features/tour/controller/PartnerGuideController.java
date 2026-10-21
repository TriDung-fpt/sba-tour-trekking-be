package com.trekking.app.features.tour.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.tour.dto.response.PartnerGuideList;
import com.trekking.app.features.tour.repository.TourGuideRepository;
import com.trekking.app.features.tour.service.TourAccessService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/partner/guides")
@PreAuthorize("hasRole('COMPANY_MANAGER')")
public class PartnerGuideController {
    private final TourAccessService access;
    private final TourGuideRepository guides;

    public PartnerGuideController(TourAccessService access, TourGuideRepository guides) {
        this.access = access; this.guides = guides;
    }

    @GetMapping
    public PartnerGuideList list(@AuthenticationPrincipal UserPrincipal user) {
        List<Long> companyIds = access.activeManagerCompanies(user.accountId());
        if (companyIds.isEmpty()) return new PartnerGuideList(List.of());
        var rows = guides.findActiveGuideOptions(companyIds, AccountStatus.ACTIVE);
        var items = rows.stream().map(row -> new PartnerGuideList.Item(((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(), (String) row[2], true)).toList();
        return new PartnerGuideList(items);
    }
}
