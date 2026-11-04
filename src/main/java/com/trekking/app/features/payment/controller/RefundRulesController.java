package com.trekking.app.features.payment.controller;

import com.trekking.app.features.payment.dto.request.SaveRefundRulesRequest;
import com.trekking.app.features.payment.dto.response.RefundRulesResponse;
import com.trekking.app.features.payment.service.RefundRulesService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/refund-rules")
public class RefundRulesController {
    private final RefundRulesService rules;

    public RefundRulesController(RefundRulesService rules) { this.rules = rules; }

    @GetMapping
    public RefundRulesResponse current() { return rules.current(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundRulesResponse save(@Valid @RequestBody SaveRefundRulesRequest request) {
        return rules.save(request);
    }
}
