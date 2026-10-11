package com.trekking.app.features.account.service.google;

public record GoogleIdentity(String subject, String email, String fullName, boolean emailAuthoritative) {}
