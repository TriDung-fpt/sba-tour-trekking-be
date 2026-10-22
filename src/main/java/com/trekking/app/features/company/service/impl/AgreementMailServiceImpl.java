package com.trekking.app.features.company.service.impl;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.features.company.service.AgreementMailService;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AgreementMailServiceImpl implements AgreementMailService {
    private final JavaMailSender sender;
    private final AuthProperties properties;

    public AgreementMailServiceImpl(JavaMailSender sender, AuthProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String otp, long companyId, long policyVersionId) {
        if (properties.mailFrom() == null || properties.mailFrom().isBlank()) throw new MailSendException("Mail not configured");
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject("Trekking - Xac nhan dieu khoan hop tac");
        message.setText("Ma xac nhan dieu khoan: " + otp + "\nCong ty: " + companyId
                + "\nPhien ban: " + policyVersionId + "\nMa co hieu luc 5 phut. Khong chia se ma.");
        sender.send(message);
    }
}
