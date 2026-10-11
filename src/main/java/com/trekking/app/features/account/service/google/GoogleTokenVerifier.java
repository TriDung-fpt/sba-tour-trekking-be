package com.trekking.app.features.account.service.google;

public interface GoogleTokenVerifier {
    GoogleIdentity verify(String idToken);
}
