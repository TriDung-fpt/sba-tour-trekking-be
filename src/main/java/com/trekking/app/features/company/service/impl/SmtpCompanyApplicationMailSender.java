package com.trekking.app.features.company.service.impl;

import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.company.service.CompanyApplicationMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpCompanyApplicationMailSender implements CompanyApplicationMailSender {
    private final JavaMailSender sender;
    private final String from;

    public SmtpCompanyApplicationMailSender(JavaMailSender sender, @Value("${app.auth.mail-from:}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void sendActivation(String email, String activationUrl) {
        var message = new SimpleMailMessage();
        message.setFrom(fromAddress());
        message.setTo(email);
        message.setSubject("WeTrek - Kích hoạt tài khoản đối tác");
        message.setText("Mở liên kết sau để xác nhận email và tự đặt mật khẩu. Liên kết chỉ dùng một lần và hết hạn sau 24 giờ:\n\n"
                + activationUrl + "\n\nNếu bạn không đăng ký, hãy bỏ qua email này.");
        sender.send(message);
    }

    @Override
    public void sendDecision(String email, CompanyStatus status, String reason) {
        var message = new SimpleMailMessage();
        message.setFrom(fromAddress());
        message.setTo(email);
        message.setSubject(status == CompanyStatus.APPROVED ? "WeTrek - Hồ sơ đối tác đã được duyệt" : "WeTrek - Cập nhật hồ sơ đối tác");
        message.setText(status == CompanyStatus.APPROVED
                ? "Hồ sơ đối tác của bạn đã được duyệt. Hãy đăng nhập để bắt đầu quản lý tour."
                : "Hồ sơ đối tác chưa được duyệt. Lý do: " + (reason == null || reason.isBlank() ? "Vui lòng liên hệ WeTrek để được hỗ trợ." : reason));
        sender.send(message);
    }

    private String fromAddress() {
        if (from == null || from.isBlank()) throw new MailSendException("Mail is not configured");
        return from;
    }
}
