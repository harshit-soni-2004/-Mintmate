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

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtProperties properties = new JwtProperties();
        properties.setSecret("local-dev-only-change-me-spendsense-jwt-hs256-key-32b");
        properties.setExpirationMs(3_600_000L);
        authService = new AuthService(userRepository, passwordEncoder, new JwtService(properties));
    }

    @Test
    void registerPersistsNormalizedEmailAndReturnsToken() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(new RegisterRequest("  Ada@Example.com ", "password1", "Ada", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(passwordEncoder.matches("password1", saved.getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("ada@example.com");
        assertThat(response.user().displayName()).isEqualTo("Ada");
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
}
