package com.trekking.app.features.company.service;

import com.trekking.app.features.company.dto.request.ActivateCompanyApplicationRequest;
import com.trekking.app.features.company.dto.request.CompanyApplicationRequest;
import com.trekking.app.features.company.dto.request.RejectCompanyApplicationRequest;
import com.trekking.app.features.company.dto.response.CompanyApplicationQueueItem;
import com.trekking.app.features.company.dto.response.CompanyApplicationStatusResponse;
import java.util.List;

public interface CompanyApplicationService {
    void apply(CompanyApplicationRequest request);
    void resendActivation(String email);
    boolean hasUnverifiedPendingApplication(long accountId);
    void activate(ActivateCompanyApplicationRequest request);
    CompanyApplicationStatusResponse status(long accountId);
    List<CompanyApplicationQueueItem> pendingApplications();
    CompanyApplicationStatusResponse approve(long actorId, long companyId);
    CompanyApplicationStatusResponse reject(long actorId, long companyId, RejectCompanyApplicationRequest request);
}
