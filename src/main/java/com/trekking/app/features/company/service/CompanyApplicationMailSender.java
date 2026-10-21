package com.trekking.app.features.company.service;

import com.trekking.app.features.company.enums.CompanyStatus;

public interface CompanyApplicationMailSender {
    void sendActivation(String email, String activationUrl);
    void sendDecision(String email, CompanyStatus status, String reason);
}
