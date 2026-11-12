package com.trekking.app.features.payment.service.impl;

import com.trekking.app.features.payment.service.RefundMailSender;
import com.trekking.app.features.payment.service.RefundMailSender.RefundNotice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails the customer only after the refund change is committed; a mail failure never undoes the change. */
@Component
public class RefundNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(RefundNotificationListener.class);
    private final RefundMailSender mail;

    public RefundNotificationListener(RefundMailSender mail) {
        this.mail = mail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundStatusChanged(RefundNotice notice) {
        if (notice.email() == null || notice.email().isBlank()) return;
        try {
            mail.send(notice);
        } catch (RuntimeException ex) {
            // Never log the address or bank data.
            log.warn("Refund email could not be sent; bookingCode={} status={}", notice.bookingCode(), notice.status());
        }
    }
}
