package com.trekking.app.features.tour.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

public final class TourReadAccess {
    private TourReadAccess() {}

    public static boolean isPublic(Authentication authentication) {
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }
}
