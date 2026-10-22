package com.trekking.app.features.invitation.service.impl;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.features.invitation.service.InvitationMailSender;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpInvitationMailSender implements InvitationMailSender {
    private final JavaMailSender sender;
    private final AuthProperties properties;

    public SmtpInvitationMailSender(JavaMailSender sender, AuthProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String otp) {
        if (properties.mailFrom() == null || properties.mailFrom().isBlank()) throw new MailSendException("Mail not configured");
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject("Trekking - Kich hoat tai khoan duoc moi");
        message.setText("Ma kich hoat tai khoan cua ban: " + otp
                + "\nMa co hieu luc trong 5 phut. Dung ma nay de xac minh email va dat mat khau ban dau."
                + "\nKhong chia se ma nay cho nguoi khac.");
        sender.send(message);
    }
}
