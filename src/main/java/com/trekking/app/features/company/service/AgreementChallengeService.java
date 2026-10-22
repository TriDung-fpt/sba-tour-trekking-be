package com.trekking.app.features.company.service;

public interface AgreementChallengeService {
    void issue(long accountId, long companyId, String email, String context, String otp);
    void consume(long accountId, long companyId, String context, String otp);
    void cancel(long accountId, long companyId, String context);
}
