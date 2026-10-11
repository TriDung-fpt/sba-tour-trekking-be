package com.trekking.app.features.auth.service;

import com.trekking.app.features.auth.dto.request.AuthRequests;
import com.trekking.app.features.otp.enums.OtpPurpose;
import com.trekking.app.features.auth.service.IssuedTokens;

public interface AuthService {
    void register(AuthRequests.Register request);
    void verifyEmail(AuthRequests.VerifyEmail request);
    void requestOtp(String email, OtpPurpose purpose);
    void resetPassword(AuthRequests.ResetPassword request);
    IssuedTokens login(AuthRequests.Login request, String ip);
    IssuedTokens refresh(String token);
    void logout(String token);
}
