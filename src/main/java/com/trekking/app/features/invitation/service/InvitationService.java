package com.trekking.app.features.invitation.service;

import com.trekking.app.features.invitation.dto.request.InvitationRequests;
import com.trekking.app.features.invitation.dto.response.InvitationResponse;

public interface InvitationService {
    InvitationResponse inviteStaff(InvitationRequests.Staff request);
    InvitationResponse inviteGuide(long companyId, InvitationRequests.Guide request);
    InvitationResponse resendStaff(long accountId);
    InvitationResponse resendGuide(long companyId, long accountId);
    void activate(InvitationRequests.Activate request);
}
