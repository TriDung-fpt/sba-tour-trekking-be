package com.trekking.app.features.payment.controller;

import com.trekking.app.features.payment.service.PaymentService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.Webhook;

@RestController
@RequestMapping("/api/v1/payments/payos")
public class PayOSWebhookController {
    private final PaymentService service;
    public PayOSWebhookController(PaymentService service) { this.service = service; }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> webhook(@RequestBody Webhook webhook) {
        service.handlePayOSWebhook(webhook);
        return Map.of("code", "00", "desc", "success", "success", true);
    }
}
