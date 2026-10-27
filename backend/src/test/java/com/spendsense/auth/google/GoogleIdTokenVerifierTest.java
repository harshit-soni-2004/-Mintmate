package com.spendsense.auth.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.spendsense.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";

    @Mock
    private GoogleIdTokenVerifier mockVerifier;

    private GoogleIdTokenVerifierImpl verifierService;

    @BeforeEach
    void setUp() {
        verifierService = new GoogleIdTokenVerifierImpl(CLIENT_ID, mockVerifier);
    }

    @Test
    @DisplayName("Valid token returns verified claims")
    void testValidToken() throws GeneralSecurityException, IOException {
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-12345");
        payload.setEmail("user@example.com");
        payload.setEmailVerified(true);
        payload.setIssuer("https://accounts.google.com");
        payload.set("name", "Test User");

        when(mockVerifier.verify("valid-token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(idToken.verifyAudience(Collections.singletonList(CLIENT_ID))).thenReturn(true);

        GoogleTokenClaims claims = verifierService.verify("valid-token");

        assertThat(claims.sub()).isEqualTo("google-sub-12345");
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.name()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Wrong audience throws 401 INVALID_GOOGLE_TOKEN")
    void testWrongAudience() throws GeneralSecurityException, IOException {
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-123");
        payload.setEmail("user@example.com");
        payload.setEmailVerified(true);
        payload.setIssuer("https://accounts.google.com");

        when(mockVerifier.verify("bad-aud-token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(idToken.verifyAudience(anyList())).thenReturn(false);

        assertThatThrownBy(() -> verifierService.verify("bad-aud-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_GOOGLE_TOKEN");
                });
    }

    @Test
    @DisplayName("Wrong issuer throws 401 INVALID_GOOGLE_TOKEN")
    void testWrongIssuer() throws GeneralSecurityException, IOException {
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-123");
        payload.setEmail("user@example.com");
        payload.setEmailVerified(true);
        payload.setIssuer("https://malicious-issuer.com");

        when(mockVerifier.verify("bad-issuer-token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(idToken.verifyAudience(anyList())).thenReturn(true);

        assertThatThrownBy(() -> verifierService.verify("bad-issuer-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_GOOGLE_TOKEN");
                });
    }

    @Test
    @DisplayName("Expired or unparseable token returns null and throws 401 INVALID_GOOGLE_TOKEN")
    void testExpiredToken() throws GeneralSecurityException, IOException {
        when(mockVerifier.verify("expired-token")).thenReturn(null);

        assertThatThrownBy(() -> verifierService.verify("expired-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_GOOGLE_TOKEN");
                });
    }

    @Test
    @DisplayName("Email not verified by Google throws 401 EMAIL_NOT_VERIFIED")
    void testEmailNotVerified() throws GeneralSecurityException, IOException {
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-123");
        payload.setEmail("unverified@example.com");
        payload.setEmailVerified(false);
        payload.setIssuer("https://accounts.google.com");

        when(mockVerifier.verify("unverified-email-token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(idToken.verifyAudience(anyList())).thenReturn(true);

        assertThatThrownBy(() -> verifierService.verify("unverified-email-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(apiEx.getCode()).isEqualTo("EMAIL_NOT_VERIFIED");
                });
    }

    @Test
    @DisplayName("Blank or null token throws 401 INVALID_GOOGLE_TOKEN")
    void testBlankToken() {
        assertThatThrownBy(() -> verifierService.verify("   "))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_GOOGLE_TOKEN"));
    }
}
