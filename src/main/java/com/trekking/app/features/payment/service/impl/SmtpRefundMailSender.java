package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.features.payment.service.RefundMailSender;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpRefundMailSender implements RefundMailSender {
    private final JavaMailSender sender;
    private final AuthProperties properties;

    public SmtpRefundMailSender(JavaMailSender sender, AuthProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void send(RefundNotice notice) {
        if (properties.mailFrom() == null || properties.mailFrom().isBlank()) throw new MailSendException("Mail not configured");
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(notice.email());
        message.setSubject("Trekking - " + headline(notice.status()) + " - don " + notice.bookingCode());
        message.setText(body(notice));
        sender.send(message);
    }

    private static String body(RefundNotice notice) {
        var text = new StringBuilder("Xin chao ").append(notice.customerName() == null ? "ban" : notice.customerName())
                .append(",\n\n");
        String amount = money(notice.amountVnd());
        String account = notice.accountNumberMasked() == null ? "" : " vao tai khoan " + notice.accountNumberMasked();
        switch (notice.status()) {
            case "requested" -> text.append("Chung toi da nhan yeu cau hoan tien ").append(amount).append(" cho don ")
                    .append(notice.bookingCode()).append(". Yeu cau dang cho bo phan tai chinh kiem tra.");
            case "approved" -> text.append("Yeu cau hoan tien cho don ").append(notice.bookingCode())
                    .append(" da duoc duyet voi so tien ").append(amount)
                    .append(". Tien se duoc chuyen").append(account).append(" trong thoi gian som nhat.");
            case "rejected" -> text.append("Yeu cau hoan tien cho don ").append(notice.bookingCode())
                    .append(" da bi tu choi.");
            case "completed" -> text.append("Da hoan ").append(amount).append(" cho don ").append(notice.bookingCode())
                    .append(account).append(". Vui long kiem tra tai khoan ngan hang cua ban.");
            default -> text.append("Yeu cau hoan tien cho don ").append(notice.bookingCode())
                    .append(" da chuyen sang trang thai ").append(notice.status()).append('.');
        }
        if (notice.note() != null && !notice.note().isBlank()) text.append("\nGhi chu: ").append(notice.note());
        return text.append("\n\nKhong chia se thong tin tai khoan ngan hang voi nguoi khac.\nTrekking").toString();
    }

    private static String headline(String status) {
        return switch (status) {
            case "requested" -> "Da nhan yeu cau hoan tien";
            case "approved" -> "Yeu cau hoan tien da duoc duyet";
            case "rejected" -> "Yeu cau hoan tien bi tu choi";
            case "completed" -> "Da hoan tien";
            default -> "Cap nhat hoan tien";
        };
    }

    /** 1500000 -> "1.500.000 VND". */
    static String money(long amountVnd) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amountVnd) + " VND";
    }
}
