package com.trekking.app.features.account.service.otp;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.features.account.enums.OtpPurpose;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpOtpMailSender implements OtpMailSender {
    private final JavaMailSender sender;
    private final AuthProperties properties;

    public SmtpOtpMailSender(JavaMailSender sender, AuthProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String otp, OtpPurpose purpose) {
        if (properties.mailFrom() == null || properties.mailFrom().isBlank()) throw new MailSendException("Mail not configured");
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject(purpose == OtpPurpose.VERIFY_EMAIL ? "Trekking - Xac thuc email" : "Trekking - Dat lai mat khau");
        message.setText("Ma OTP cua ban: " + otp + "\nMa co hieu luc trong 5 phut. Khong chia se ma nay cho nguoi khac.");
        sender.send(message);
    }
}
