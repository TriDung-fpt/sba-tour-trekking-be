package com.trekking.app.features.company.service;

import com.trekking.app.features.company.dto.request.CompanyDecisionRequest;
import com.trekking.app.features.company.dto.request.CompanyProfileRequest;
import com.trekking.app.features.company.dto.response.CompanyLicenseResponse;
import com.trekking.app.features.company.dto.response.CompanyPageResponse;
import com.trekking.app.features.company.dto.response.CompanyResponse;
import org.springframework.web.multipart.MultipartFile;

public interface CompanyService {
    CompanyResponse create(CompanyProfileRequest request);
    CompanyPageResponse list(int page, int size);
    CompanyResponse read(long companyId);
    CompanyResponse update(long companyId, CompanyProfileRequest request);
    CompanyResponse resubmit(long companyId);
    CompanyPageResponse reviewList(String status, int page, int size);
    CompanyResponse reviewRead(long companyId);
    CompanyResponse decide(long companyId, CompanyDecisionRequest request);
    CompanyResponse uploadLicense(long companyId, MultipartFile file);
    CompanyLicenseResponse license(long companyId, boolean review);
}
