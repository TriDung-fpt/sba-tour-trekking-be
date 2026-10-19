package com.trekking.app.features.invitation.service;

public interface InvitationMailSender {
    void send(String email, String otp);
}
