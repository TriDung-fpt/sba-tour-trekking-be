package com.trekking.app.features.invitation.controller;

import com.trekking.app.features.auth.dto.response.MessageResponse;
import com.trekking.app.features.invitation.dto.request.InvitationRequests;
import com.trekking.app.features.invitation.dto.response.InvitationResponse;
import com.trekking.app.features.invitation.service.InvitationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvitationController {
    private final InvitationService invitations;

    public InvitationController(InvitationService invitations) { this.invitations = invitations; }

    @PostMapping("/api/v1/admin/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    public InvitationResponse invite(@Valid @RequestBody InvitationRequests.Invite request) {
        return invitations.invite(request);
    }

    @PostMapping("/api/v1/auth/activate-account")
    public MessageResponse activate(@Valid @RequestBody InvitationRequests.Activate request) {
        invitations.activate(request);
        return new MessageResponse("Account activated; sign in with your new password");
    }

    @PostMapping("/api/v1/auth/resend-account-activation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendActivation(@Valid @RequestBody InvitationRequests.ResendActivation request) {
        invitations.resendActivation(request);
        return new MessageResponse("If the account is eligible, an activation code will be sent");
    }
}
