package com.trekking.app.features.otp.service;

import com.trekking.app.features.otp.enums.OtpPurpose;

public interface OtpMailSender {
    void send(String email, String otp, OtpPurpose purpose);
}
