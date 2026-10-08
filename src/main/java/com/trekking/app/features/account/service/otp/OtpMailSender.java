package com.trekking.app.features.account.service.otp;

import com.trekking.app.features.account.enums.OtpPurpose;

public interface OtpMailSender {
    void send(String email, String otp, OtpPurpose purpose);
}
