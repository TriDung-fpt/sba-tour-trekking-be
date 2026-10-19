package com.trekking.app.features.invitation.controller;

import com.trekking.app.features.auth.dto.response.MessageResponse;
import com.trekking.app.features.invitation.dto.request.InvitationRequests;
import com.trekking.app.features.invitation.dto.response.InvitationResponse;
import com.trekking.app.features.invitation.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvitationController {
    private final InvitationService invitations;

    public InvitationController(InvitationService invitations) { this.invitations = invitations; }

    @PostMapping("/api/v1/admin/staff/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse staff(@Valid @RequestBody InvitationRequests.Staff request) {
        return invitations.inviteStaff(request);
    }

    @PostMapping("/api/v1/companies/{companyId}/guides/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse guide(@PathVariable long companyId, @Valid @RequestBody InvitationRequests.Guide request) {
        return invitations.inviteGuide(companyId, request);
    }

    @PostMapping("/api/v1/admin/staff/invitations/{accountId}/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InvitationResponse resendStaff(@PathVariable long accountId) { return invitations.resendStaff(accountId); }

    @PostMapping("/api/v1/companies/{companyId}/guides/invitations/{accountId}/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InvitationResponse resendGuide(@PathVariable long companyId, @PathVariable long accountId) {
        return invitations.resendGuide(companyId, accountId);
    }

    @PostMapping("/api/v1/auth/activate-account")
    public MessageResponse activate(@Valid @RequestBody InvitationRequests.Activate request) {
        invitations.activate(request);
        return new MessageResponse("Account activated; sign in with your new password");
    }
}
