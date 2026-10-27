package com.spendsense.auth.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.spendsense.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class GoogleIdTokenVerifierImpl implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifierImpl.class);

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleIdTokenVerifierImpl(@Value("${spendsense.auth.google.client-id:${GOOGLE_CLIENT_ID:643600067186-lfnqh2n2lagtevutnpbc0shfu4mmj0gp.apps.googleusercontent.com}}") String clientId) {
        this(clientId, new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience((clientId != null && !clientId.trim().isEmpty()) ? Collections.singletonList(clientId.trim()) : Collections.emptyList())
                .setIssuers(List.of("https://accounts.google.com", "accounts.google.com"))
                .build());
    }

    GoogleIdTokenVerifierImpl(String clientId, GoogleIdTokenVerifier verifier) {
        this.clientId = clientId != null ? clientId.trim() : "";
        this.verifier = verifier;
    }

    @Override
    public GoogleTokenClaims verify(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google ID token is required");
        }
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString.trim());
            if (idToken == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Invalid Google ID token signature or expiration");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // Strict audience check
            if (!clientId.isEmpty() && !idToken.verifyAudience(Collections.singletonList(clientId))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google token audience mismatch");
            }

            // Strict issuer check
            String issuer = payload.getIssuer();
            if (issuer == null || (!issuer.equals("https://accounts.google.com") && !issuer.equals("accounts.google.com"))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google token issuer invalid");
            }

            // Strict email_verified check
            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified == null || !emailVerified) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "EMAIL_NOT_VERIFIED", "Google account email is not verified");
            }

            String email = payload.getEmail();
            String sub = payload.getSubject();
            String name = (String) payload.get("name");

            if (email == null || email.isBlank() || sub == null || sub.isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google token missing required claims");
            }

            return new GoogleTokenClaims(sub, email, true, name != null ? name : email.split("@")[0]);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to verify Google ID token: {}", e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Failed to verify Google ID token");
        }
    }
}
