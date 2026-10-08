package com.trekking.app.features.account.dto.response;

import java.util.List;

public record AccountResponse(Long id, String email, String fullName, String phone, List<String> roles) {}
