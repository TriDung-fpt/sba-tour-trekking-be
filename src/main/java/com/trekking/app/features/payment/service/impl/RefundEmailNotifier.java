package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.config.AuthProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class RefundEmailNotifier {
    private final JavaMailSender mail;
    private final AuthProperties properties;

    public RefundEmailNotifier(JavaMailSender mail, AuthProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    public void send(String email, long bookingId, long amountVnd, String status) {
        if (email == null || email.isBlank() || properties.mailFrom() == null || properties.mailFrom().isBlank()) return;
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject("Trekking - Cập nhật yêu cầu hoàn tiền");
        message.setText("Yêu cầu hoàn tiền cho booking " + bookingId + " đã chuyển sang trạng thái " + status
                + ". Số tiền: " + amountVnd + " VND. Không chia sẻ thông tin tài khoản với người khác.");
        mail.send(message);
    }
}
