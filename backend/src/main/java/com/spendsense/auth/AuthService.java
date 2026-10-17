package com.spendsense.auth;

import com.spendsense.auth.dto.AuthResponse;
import com.spendsense.auth.dto.LoginRequest;
import com.spendsense.auth.dto.RegisterRequest;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.JwtService;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import com.spendsense.user.UserResponse;
import com.spendsense.auth.dto.GoogleLoginRequest;
import com.spendsense.auth.google.GoogleTokenClaims;
import com.spendsense.auth.google.GoogleTokenVerifier;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this(userRepository, passwordEncoder, jwtService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) GoogleTokenVerifier googleTokenVerifier
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
        }
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? DEFAULT_TIMEZONE
                : request.timezone().trim();
        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                timezone,
                null,
                false
        );
        userRepository.save(user);
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        if (googleTokenVerifier == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_AUTH_UNAVAILABLE", "Google authentication is not configured");
        }
        GoogleTokenClaims claims = googleTokenVerifier.verify(request.idToken());
        String email = normalizeEmail(claims.email());
        String sub = claims.sub();

        // 1. User with this google_sub exists -> log in
        Optional<User> bySub = userRepository.findByGoogleSub(sub);
        if (bySub.isPresent()) {
            return toAuthResponse(bySub.get());
        }

        // 2. No sub match, but user with same email exists
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.setGoogleSub(sub);
            if (existing.isEmailVerified()) {
                // Email is already verified, link sub and log in
                userRepository.save(existing);
                return toAuthResponse(existing);
            } else {
                // Pre-registration takeover risk: link sub, mark verified, invalidate password
                existing.setEmailVerified(true);
                existing.invalidatePassword();
                userRepository.save(existing);
                return toAuthResponse(existing);
            }
        }

        // 3. No user exists -> create one (verified email, no password), log in
        String displayName = (claims.name() != null && !claims.name().isBlank())
                ? claims.name().trim()
                : email.split("@")[0];
        if (displayName.length() > 80) {
            displayName = displayName.substring(0, 80);
        }

        User newUser = new User(
                email,
                null,
                displayName,
                DEFAULT_TIMEZONE,
                sub,
                true
        );
        userRepository.save(newUser);
        return toAuthResponse(newUser);
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.bearer(jwtService.issueToken(user), UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
    }
}
