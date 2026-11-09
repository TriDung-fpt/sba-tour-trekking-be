package com.trekking.app.features.invitation.service;

import com.trekking.app.features.invitation.dto.request.InvitationRequests;
import com.trekking.app.features.invitation.dto.response.InvitationResponse;

public interface InvitationService {
    InvitationResponse invite(InvitationRequests.Invite request);
    void resendActivation(InvitationRequests.ResendActivation request);
    void activate(InvitationRequests.Activate request);
}
