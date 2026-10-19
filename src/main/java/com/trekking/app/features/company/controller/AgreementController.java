package com.trekking.app.features.company.controller;

import com.trekking.app.features.company.dto.request.AgreementAcceptRequest;
import com.trekking.app.features.company.dto.request.AgreementOtpRequest;
import com.trekking.app.features.company.dto.response.AgreementOtpResponse;
import com.trekking.app.features.company.dto.response.AgreementReceiptResponse;
import com.trekking.app.features.company.dto.response.PolicyVersionResponse;
import com.trekking.app.features.company.service.AgreementService;
import com.trekking.app.features.company.service.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}")
public class AgreementController {
    private final AgreementService agreements;
    private final PolicyService policies;

    public AgreementController(AgreementService agreements, PolicyService policies) {
        this.agreements = agreements;
        this.policies = policies;
    }

    @GetMapping("/agreement-policies")
    public List<PolicyVersionResponse> policies(@PathVariable long companyId) { return policies.eligible(companyId); }

    @PostMapping("/agreements/otp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AgreementOtpResponse requestOtp(@PathVariable long companyId, @Valid @RequestBody AgreementOtpRequest request) {
        return agreements.requestOtp(companyId, request);
    }

    @PostMapping("/agreements")
    @ResponseStatus(HttpStatus.CREATED)
    public AgreementReceiptResponse accept(@PathVariable long companyId, @Valid @RequestBody AgreementAcceptRequest body,
                                           HttpServletRequest request) {
        return agreements.accept(companyId, body, request.getRemoteAddr());
    }

    @GetMapping("/agreements")
    public List<AgreementReceiptResponse> receipts(@PathVariable long companyId) { return agreements.receipts(companyId); }

    @GetMapping("/agreements/{agreementId}")
    public AgreementReceiptResponse receipt(@PathVariable long companyId, @PathVariable long agreementId) {
        return agreements.receipt(companyId, agreementId);
    }
}
