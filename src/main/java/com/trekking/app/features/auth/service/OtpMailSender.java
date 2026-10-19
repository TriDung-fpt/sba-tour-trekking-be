package com.trekking.app.features.auth.service;

import com.trekking.app.features.auth.enums.OtpPurpose;

public interface OtpMailSender {
    void send(String email, String otp, OtpPurpose purpose);
}
