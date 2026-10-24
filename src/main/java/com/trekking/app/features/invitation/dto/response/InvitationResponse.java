package com.trekking.app.features.invitation.dto.response;

public record InvitationResponse(long accountId, String email, String role,
                                 boolean activationRequired, String deliveryStatus) {}
