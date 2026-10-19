package com.trekking.app.features.account.controller;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.account.dto.response.AuthorizationResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/me")
@SecurityRequirement(name = "bearerAuth")
public class AccountAuthorizationController {
    private final AuthorizationService authorization;

    public AccountAuthorizationController(AuthorizationService authorization) { this.authorization = authorization; }

    @GetMapping("/authorization")
    public AuthorizationResponse authorization() { return authorization.authorization(); }
}
