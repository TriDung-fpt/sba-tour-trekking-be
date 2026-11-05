package com.trekking.app.features.policy.service;

import com.trekking.app.features.policy.dto.response.PolicyDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface PolicyDocumentService {
    PolicyDocumentResponse extract(MultipartFile file);
}
