package com.trekking.app.features.company.service;

import com.trekking.app.features.company.dto.response.CompanyLicenseResponse;
import org.springframework.web.multipart.MultipartFile;

public interface CompanyLicenseStorageService {
    String store(MultipartFile file);
    CompanyLicenseResponse read(String reference);
    void delete(String reference);
}
