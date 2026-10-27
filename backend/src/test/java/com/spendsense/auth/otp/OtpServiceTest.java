package com.spendsense.auth.otp;

import com.spendsense.auth.dto.OtpResponse;
import com.spendsense.auth.mail.EmailService;
import com.spendsense.common.exception.ApiException;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private EmailOtpRepository otpRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    private OtpRateLimiter rateLimiter;
    private OtpService otpService;

    private static final String TEST_SECRET = "TestSecretKeyForHmacSigning1234567890!";
    private static final String TEST_EMAIL = "alex@example.com";

    @BeforeEach
    void setUp() {
        rateLimiter = new OtpRateLimiter();
        otpService = new OtpService(
                otpRepository,
                userRepository,
                emailService,
                rateLimiter,
                TEST_SECRET
        );
    }

    @Test
    @DisplayName("Send OTP: Successfully generates OTP, stores only HMAC hash, and sends plain code via email")
    void sendOtp_successfulGenerationAndHashOnlyStorage() {
        User user = new User(TEST_EMAIL, "hashedpw", "Alex", "Asia/Kolkata", null, false);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.empty());
        when(otpRepository.countByEmailAndCreatedAtAfter(eq(TEST_EMAIL), any(Instant.class)))
                .thenReturn(0L);

        OtpResponse response = otpService.sendVerificationOtp(TEST_EMAIL, "EMAIL_VERIFICATION", "192.168.1.1");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResendCooldownSeconds()).isEqualTo(60);

        ArgumentCaptor<EmailOtp> otpCaptor = ArgumentCaptor.forClass(EmailOtp.class);
        verify(otpRepository).save(otpCaptor.capture());
        EmailOtp savedOtp = otpCaptor.getValue();

        assertThat(savedOtp.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(savedOtp.getCodeHash()).isNotBlank();
        // Crucial security test: hash must NEVER be a 6-digit plain code
        assertThat(savedOtp.getCodeHash()).doesNotMatch("^\\d{6}$");
        assertThat(savedOtp.getExpiresAt()).isAfter(Instant.now().plus(9, ChronoUnit.MINUTES));

        ArgumentCaptor<String> plainCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationOtp(eq(TEST_EMAIL), plainCodeCaptor.capture());
        String sentPlainCode = plainCodeCaptor.getValue();
        assertThat(sentPlainCode).matches("^\\d{6}$");

        // The hash of the sent code must match the stored hash
        assertThat(otpService.computeHmac(sentPlainCode)).isEqualTo(savedOtp.getCodeHash());
    }

    @Test
    @DisplayName("No User Enumeration: sendOtp returns the exact same success response when email is not registered")
    void sendOtp_noUserEnumerationWhenUserDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc("unknown@example.com", "EMAIL_VERIFICATION"))
                .thenReturn(Optional.empty());
        when(otpRepository.countByEmailAndCreatedAtAfter(eq("unknown@example.com"), any(Instant.class)))
                .thenReturn(0L);

        OtpResponse response = otpService.sendVerificationOtp("unknown@example.com", "EMAIL_VERIFICATION", "192.168.1.1");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("If an account is associated with this email");
        // No OTP should be stored or sent for non-existent users
        verify(otpRepository, never()).save(any());
        verify(emailService, never()).sendVerificationOtp(any(), any());
    }

    @Test
    @DisplayName("Resend Cooldown: rejected if requested within 60 seconds")
    void sendOtp_rejectedDuringCooldown() {
        EmailOtp activeOtp = new EmailOtp(TEST_EMAIL, "EMAIL_VERIFICATION", "hash", Instant.now().plus(9, ChronoUnit.MINUTES));
        activeOtp.setCreatedAt(Instant.now().minusSeconds(30)); // 30s ago (< 60s)
        when(otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(activeOtp));

        assertThatThrownBy(() -> otpService.sendVerificationOtp(TEST_EMAIL, "EMAIL_VERIFICATION", "192.168.1.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(apiEx.getCode()).isEqualTo("OTP_COOLDOWN");
                });

        verify(emailService, never()).sendVerificationOtp(any(), any());
    }

    @Test
    @DisplayName("Rate Limit Email: rejected if email exceeds 5 requests per hour")
    void sendOtp_rejectedWhenEmailExceedsHourlyLimit() {
        when(otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.empty());
        when(otpRepository.countByEmailAndCreatedAtAfter(eq(TEST_EMAIL), any(Instant.class)))
                .thenReturn(5L);

        assertThatThrownBy(() -> otpService.sendVerificationOtp(TEST_EMAIL, "EMAIL_VERIFICATION", "192.168.1.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(apiEx.getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
                });
    }

    @Test
    @DisplayName("Rate Limit IP: rejected if single IP exceeds 20 requests per hour")
    void sendOtp_rejectedWhenIpExceedsLimit() {
        String ip = "10.0.0.99";
        for (int i = 0; i < 20; i++) {
            assertThat(rateLimiter.isAllowed(ip)).isTrue();
        }
        // 21st attempt
        assertThatThrownBy(() -> otpService.sendVerificationOtp(TEST_EMAIL, "EMAIL_VERIFICATION", ip))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(apiEx.getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
                });
    }

    @Test
    @DisplayName("Verify OTP: Correct code successfully verifies and sets email_verified = true on user")
    void verifyOtp_correctCodeMarksUserVerified() {
        String plainCode = "654321";
        String codeHash = otpService.computeHmac(plainCode);
        EmailOtp otp = new EmailOtp(TEST_EMAIL, "EMAIL_VERIFICATION", codeHash, Instant.now().plus(10, ChronoUnit.MINUTES));

        User user = new User(TEST_EMAIL, "hashedpw", "Alex", "Asia/Kolkata", null, false);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(otp));

        OtpResponse response = otpService.verifyOtp(TEST_EMAIL, plainCode, "EMAIL_VERIFICATION");

        assertThat(response.isSuccess()).isTrue();
        assertThat(otp.isConsumed()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
        verify(otpRepository).save(otp);
    }

    @Test
    @DisplayName("Verify OTP: Wrong code increments attempts and fails with INVALID_OTP")
    void verifyOtp_wrongCodeIncrementsAttempts() {
        String codeHash = otpService.computeHmac("123456");
        EmailOtp otp = new EmailOtp(TEST_EMAIL, "EMAIL_VERIFICATION", codeHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        assertThat(otp.getAttempts()).isEqualTo(0);

        when(otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, "999999", "EMAIL_VERIFICATION"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_OTP");
                });

        assertThat(otp.getAttempts()).isEqualTo(1);
        verify(otpRepository).save(otp);
    }

    @Test
    @DisplayName("Verify OTP: Expired OTP rejected with OTP_EXPIRED")
    void verifyOtp_expiredOtpRejected() {
        String codeHash = otpService.computeHmac("123456");
        EmailOtp otp = new EmailOtp(TEST_EMAIL, "EMAIL_VERIFICATION", codeHash, Instant.now().minus(1, ChronoUnit.MINUTES));

        when(otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, "123456", "EMAIL_VERIFICATION"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("OTP_EXPIRED");
                });
    }

    @Test
    @DisplayName("Verify OTP: Exceeding max 5 attempts invalidates OTP with OTP_MAX_ATTEMPTS")
    void verifyOtp_maxAttemptsReachedInvalidates() {
        String codeHash = otpService.computeHmac("123456");
        EmailOtp otp = new EmailOtp(TEST_EMAIL, "EMAIL_VERIFICATION", codeHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        otp.setAttempts(4); // 4 prior failures

        when(otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(otp));

        // 5th failure
        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, "000000", "EMAIL_VERIFICATION"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("OTP_MAX_ATTEMPTS");
                });

        assertThat(otp.getAttempts()).isEqualTo(5);
        assertThat(otp.isExhausted()).isTrue();
    }

    @Test
    @DisplayName("Verify OTP: Already consumed OTP cannot be reused")
    void verifyOtp_consumedOtpCannotBeReused() {
        // Query returns empty because findTopByEmailAndPurposeAndConsumedAtIsNull looks only for unconsumed OTPs
        when(otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL, "EMAIL_VERIFICATION"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, "123456", "EMAIL_VERIFICATION"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_OTP");
                });
    }
}
