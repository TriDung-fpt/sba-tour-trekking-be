package com.trekking.app.features.auth.service;

import com.trekking.app.features.auth.model.GoogleIdentity;

public interface GoogleTokenVerifier {
    GoogleIdentity verify(String idToken);
}
