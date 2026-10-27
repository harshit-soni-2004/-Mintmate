package com.spendsense.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.auth.dto.LoginRequest;
import com.spendsense.auth.dto.RegisterRequest;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.JwtProperties;
import com.spendsense.security.JwtService;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.spendsense.auth.google.GoogleTokenVerifier googleTokenVerifier;

    @Mock
    private com.spendsense.auth.otp.OtpService otpService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtProperties properties = new JwtProperties();
        properties.setSecret("local-dev-only-change-me-spendsense-jwt-hs256-key-32b");
        properties.setExpirationMs(3_600_000L);
        authService = new AuthService(userRepository, passwordEncoder, new JwtService(properties), googleTokenVerifier, otpService);
    }

    @Test
    void registerPersistsNormalizedEmailAsUnverifiedWithoutToken() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(new RegisterRequest("  Ada@Example.com ", "password1", "Ada", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(passwordEncoder.matches("password1", saved.getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isNull();
        assertThat(response.tokenType()).isNull();
        assertThat(response.user().email()).isEqualTo("ada@example.com");
        assertThat(response.user().displayName()).isEqualTo("Ada");
        verify(otpService).sendVerificationOtp("ada@example.com", "EMAIL_VERIFICATION", null);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("ada@example.com", "password1", "Ada", null)))
                .isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(HttpStatus.CONFLICT, "EMAIL_TAKEN");
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmail("ada@example.com")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "password1")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("ada@example.com", passwordEncoder.encode("password1"), "Ada", "Asia/Kolkata");
        when(userRepository.findByEmail("ada@example.com")).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "wrongpass")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void loginRejectsUserWithNoPassword() {
        // User created via Google with password_hash = null
        User user = new User("googleuser@example.com", null, "Google User", "Asia/Kolkata", "sub-123", true);
        when(userRepository.findByEmail("googleuser@example.com")).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("googleuser@example.com", "any-password")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void loginWithGoogleNewUserCreatesAccountWithoutPasswordAndLogsIn() {
        when(googleTokenVerifier.verify("token-new")).thenReturn(
                new com.spendsense.auth.google.GoogleTokenClaims("sub-google-new", "newbie@example.com", true, "New Bie")
        );
        when(userRepository.findByGoogleSub("sub-google-new")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByEmail("newbie@example.com")).thenReturn(java.util.Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.loginWithGoogle(new com.spendsense.auth.dto.GoogleLoginRequest("token-new"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo("newbie@example.com");
        assertThat(saved.getGoogleSub()).isEqualTo("sub-google-new");
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getDisplayName()).isEqualTo("New Bie");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("newbie@example.com");
    }

    @Test
    void loginWithGoogleExistingSubMatchLogsInDirectly() {
        User existing = new User("known@example.com", null, "Known User", "Asia/Kolkata", "sub-existing", true);
        when(googleTokenVerifier.verify("token-existing")).thenReturn(
                new com.spendsense.auth.google.GoogleTokenClaims("sub-existing", "known@example.com", true, "Known User")
        );
        when(userRepository.findByGoogleSub("sub-existing")).thenReturn(java.util.Optional.of(existing));

        var response = authService.loginWithGoogle(new com.spendsense.auth.dto.GoogleLoginRequest("token-existing"));

        assertThat(response.user().email()).isEqualTo("known@example.com");
    }

    @Test
    void loginWithGoogleExistingVerifiedUserLinksSubAndPreservesPassword() {
        String existingPasswordHash = passwordEncoder.encode("existingPass123");
        User existing = new User("verified@example.com", existingPasswordHash, "Verified User", "Asia/Kolkata", null, true);

        when(googleTokenVerifier.verify("token-link")).thenReturn(
                new com.spendsense.auth.google.GoogleTokenClaims("sub-to-link", "verified@example.com", true, "Verified User")
        );
        when(userRepository.findByGoogleSub("sub-to-link")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByEmail("verified@example.com")).thenReturn(java.util.Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.loginWithGoogle(new com.spendsense.auth.dto.GoogleLoginRequest("token-link"));

        assertThat(existing.getGoogleSub()).isEqualTo("sub-to-link");
        assertThat(existing.getPasswordHash()).isEqualTo(existingPasswordHash);
        assertThat(existing.isEmailVerified()).isTrue();
        assertThat(response.user().email()).isEqualTo("verified@example.com");
    }

    @Test
    void loginWithGoogleExistingUnverifiedUserMitigatesPreRegistrationTakeover() {
        String attackerPasswordHash = passwordEncoder.encode("attackerSecret");
        User existingUnverified = new User("victim@example.com", attackerPasswordHash, "Victim", "Asia/Kolkata", null, false);

        when(googleTokenVerifier.verify("token-victim")).thenReturn(
                new com.spendsense.auth.google.GoogleTokenClaims("sub-victim", "victim@example.com", true, "Victim Real Name")
        );
        when(userRepository.findByGoogleSub("sub-victim")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByEmail("victim@example.com")).thenReturn(java.util.Optional.of(existingUnverified));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.loginWithGoogle(new com.spendsense.auth.dto.GoogleLoginRequest("token-victim"));

        // Password MUST be invalidated (set to null) so pre-registered attacker cannot log in!
        assertThat(existingUnverified.getPasswordHash()).isNull();
        assertThat(existingUnverified.getGoogleSub()).isEqualTo("sub-victim");
        assertThat(existingUnverified.isEmailVerified()).isTrue();
        assertThat(response.user().email()).isEqualTo("victim@example.com");
    }

    @Test
    void loginAllowsVerifiedUserWithCorrectPassword() {
        User verifiedUser = new User("verified@example.com", passwordEncoder.encode("correctPass1"), "Verified", "Asia/Kolkata", null, true);
        when(userRepository.findByEmail("verified@example.com")).thenReturn(java.util.Optional.of(verifiedUser));

        var response = authService.login(new LoginRequest("verified@example.com", "correctPass1"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("verified@example.com");
    }

    @Test
    void loginBlocksUnverifiedUserWithCorrectPassword() {
        User unverifiedUser = new User("unverified@example.com", passwordEncoder.encode("correctPass1"), "Unverified", "Asia/Kolkata", null, false);
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(java.util.Optional.of(unverifiedUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("unverified@example.com", "correctPass1")))
                .isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
    }

    @Test
    void loginRejectsUnverifiedUserWithWrongPasswordAsGenericInvalidCredentials() {
        User unverifiedUser = new User("unverified@example.com", passwordEncoder.encode("correctPass1"), "Unverified", "Asia/Kolkata", null, false);
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(java.util.Optional.of(unverifiedUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("unverified@example.com", "wrongPass")))
                .isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }

    @Test
    void requestPasswordResetDelegatesToOtpService() {
        when(otpService.sendVerificationOtp("user@example.com", "PASSWORD_RESET", "127.0.0.1"))
                .thenReturn(new com.spendsense.auth.dto.OtpResponse(true, "Sent", 60));

        var response = authService.requestPasswordReset("user@example.com", "127.0.0.1");

        assertThat(response.isSuccess()).isTrue();
        verify(otpService).sendVerificationOtp("user@example.com", "PASSWORD_RESET", "127.0.0.1");
    }

    @Test
    void resetPasswordUpdatesPasswordAndMarksEmailVerified() {
        User user = new User("user@example.com", passwordEncoder.encode("oldPassword1"), "User", "Asia/Kolkata", null, false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.resetPassword(new com.spendsense.auth.dto.ResetPasswordRequest(
                "user@example.com", "654321", "NewValidPassword2"
        ));

        assertThat(response.isSuccess()).isTrue();
        verify(otpService).verifyOtp("user@example.com", "654321", "PASSWORD_RESET");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(passwordEncoder.matches("NewValidPassword2", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("oldPassword1", user.getPasswordHash())).isFalse();
    }

    @Test
    void nullPasswordAccountCanSetPasswordViaReset() {
        // User created via Google with password_hash = null
        User googleUser = new User("googleonly@example.com", null, "Google Only", "Asia/Kolkata", "sub-999", true);
        when(userRepository.findByEmail("googleonly@example.com")).thenReturn(java.util.Optional.of(googleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.resetPassword(new com.spendsense.auth.dto.ResetPasswordRequest(
                "googleonly@example.com", "654321", "NewlySetPassword3"
        ));

        assertThat(response.isSuccess()).isTrue();
        assertThat(googleUser.getPasswordHash()).isNotNull();
        assertThat(passwordEncoder.matches("NewlySetPassword3", googleUser.getPasswordHash())).isTrue();
    }
}
