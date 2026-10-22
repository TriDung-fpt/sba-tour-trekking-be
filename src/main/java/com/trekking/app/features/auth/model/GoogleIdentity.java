package com.trekking.app.features.auth.model;

public record GoogleIdentity(String subject, String email, String fullName, boolean emailAuthoritative) {}
