package com.trekking.app.features.company.service;

import com.trekking.app.features.company.dto.request.AgreementAcceptRequest;
import com.trekking.app.features.company.dto.request.AgreementOtpRequest;
import com.trekking.app.features.company.dto.response.AgreementOtpResponse;
import com.trekking.app.features.company.dto.response.AgreementReceiptResponse;
import java.util.List;

public interface AgreementService {
    AgreementOtpResponse requestOtp(long companyId, AgreementOtpRequest request);
    AgreementReceiptResponse accept(long companyId, AgreementAcceptRequest request, String peerIp);
    List<AgreementReceiptResponse> receipts(long companyId);
    AgreementReceiptResponse receipt(long companyId, long agreementId);
}
