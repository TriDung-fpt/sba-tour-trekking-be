package com.trekking.app.features.company.service;

public interface AgreementMailService {
    void send(String email, String otp, long companyId, long policyVersionId);
}
