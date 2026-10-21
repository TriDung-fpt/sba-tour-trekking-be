package com.trekking.app.features.company.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.auth.dto.response.MessageResponse;
import com.trekking.app.features.company.dto.request.ActivateCompanyApplicationRequest;
import com.trekking.app.features.company.dto.request.CompanyApplicationRequest;
import com.trekking.app.features.company.dto.response.CompanyApplicationStatusResponse;
import com.trekking.app.features.company.service.CompanyApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/partner-applications")
public class CompanyApplicationController {
    private final CompanyApplicationService applications;

    public CompanyApplicationController(CompanyApplicationService applications) { this.applications = applications; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse apply(@Valid @RequestBody CompanyApplicationRequest request) {
        applications.apply(request);
        return new MessageResponse("Application received. Check your email to set a password and confirm your address.");
    }

    @PostMapping("/resend-activation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resend(@Valid @RequestBody com.trekking.app.features.auth.dto.request.AuthRequests.EmailRequest request) {
        applications.resendActivation(request.email());
        return new MessageResponse("If an eligible partner application exists, an activation link will be sent.");
    }

    @PostMapping("/activate")
    public MessageResponse activate(@Valid @RequestBody ActivateCompanyApplicationRequest request) {
        applications.activate(request);
        return new MessageResponse("Email confirmed and password set. You can now sign in to check your application.");
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public CompanyApplicationStatusResponse status(@AuthenticationPrincipal UserPrincipal principal) {
        return applications.status(principal.accountId());
    }
}
