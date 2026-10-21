package com.trekking.app.features.company.dto.request;

import com.trekking.app.features.auth.dto.request.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateCompanyApplicationRequest(@NotBlank @Size(max = 100) String token,
                                                @ValidPassword String password) {}
