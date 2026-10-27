package com.spendsense.auth.google;

public interface GoogleTokenVerifier {
    GoogleTokenClaims verify(String idToken);
}
