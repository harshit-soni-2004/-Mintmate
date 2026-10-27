package com.spendsense.auth.otp;

import com.spendsense.auth.dto.OtpResponse;
import com.spendsense.auth.mail.EmailService;
import com.spendsense.common.exception.ApiException;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final int OTP_LENGTH = 6;
    private static final long EXPIRY_MINUTES = 10;
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final long MAX_HOURLY_REQUESTS_PER_EMAIL = 5;

    private final EmailOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final OtpRateLimiter rateLimiter;
    private final String hmacSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public OtpService(
            EmailOtpRepository otpRepository,
            UserRepository userRepository,
            @Autowired(required = false) EmailService emailService,
            OtpRateLimiter rateLimiter,
            @Value("${spendsense.auth.otp.hmac-secret:SpendSenseDefaultOtpSecretKeyForHmacSigning2026!}") String hmacSecret
    ) {
        this.otpRepository = otpRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.hmacSecret = hmacSecret;
    }

    @Transactional
    public OtpResponse sendVerificationOtp(String emailInput, String purposeInput, String clientIp) {
        String email = normalizeEmail(emailInput);
        String purpose = (purposeInput == null || purposeInput.isBlank()) ? "EMAIL_VERIFICATION" : purposeInput.trim();

        // 1. IP rate limiting
        if (!rateLimiter.isAllowed(clientIp)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many verification requests. Please try again later.");
        }

        // 2. Cooldown check (60s)
        Optional<EmailOtp> latestOtp = otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose);
        if (latestOtp.isPresent()) {
            Instant createdAt = latestOtp.get().getCreatedAt();
            long secondsSince = ChronoUnit.SECONDS.between(createdAt, Instant.now());
            if (secondsSince < RESEND_COOLDOWN_SECONDS) {
                long remaining = RESEND_COOLDOWN_SECONDS - secondsSince;
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN", "Please wait " + remaining + " seconds before requesting another code.");
            }
        }

        // 3. Hourly email rate limiting
        long requestsInLastHour = otpRepository.countByEmailAndCreatedAtAfter(email, Instant.now().minus(1, ChronoUnit.HOURS));
        if (requestsInLastHour >= MAX_HOURLY_REQUESTS_PER_EMAIL) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many verification attempts for this email. Please try again in an hour.");
        }

        // 4. Generate 6-digit numeric OTP and compute HMAC hash
        String plainCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeHash = computeHmac(plainCode);

        // 5. Anti-enumeration: Check if user exists. Even if user does not exist, compute HMAC and simulate work.
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            EmailOtp otp = new EmailOtp(
                    email,
                    purpose,
                    codeHash,
                    Instant.now().plus(EXPIRY_MINUTES, ChronoUnit.MINUTES)
            );
            otp.setMaxAttempts(MAX_ATTEMPTS);
            otpRepository.save(otp);

            if (emailService != null) {
                emailService.sendVerificationOtp(email, plainCode);
            }
        } else {
            // Fake computation for timing attack parity
            computeHmac(plainCode);
        }

        return new OtpResponse(true, "If an account is associated with this email, a verification code has been sent.", RESEND_COOLDOWN_SECONDS);
    }

    @Transactional
    public OtpResponse verifyOtp(String emailInput, String codeInput, String purposeInput) {
        String email = normalizeEmail(emailInput);
        String code = codeInput != null ? codeInput.trim() : "";
        String purpose = (purposeInput == null || purposeInput.isBlank()) ? "EMAIL_VERIFICATION" : purposeInput.trim();

        if (code.length() != OTP_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Verification code must be 6 digits.");
        }

        Optional<EmailOtp> otpOpt = otpRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose);

        if (otpOpt.isEmpty()) {
            // Anti-enumeration dummy check
            computeHmac(code);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Invalid or expired verification code.");
        }

        EmailOtp otp = otpOpt.get();

        // Check if expired
        if (otp.isExpired()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "Verification code has expired. Please request a new code.");
        }

        // Check attempt limit
        if (otp.isExhausted()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_MAX_ATTEMPTS", "Maximum verification attempts reached. Please request a new code.");
        }

        // Constant-time hash verification
        String expectedHash = computeHmac(code);
        boolean matches = MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                otp.getCodeHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            if (otp.isExhausted()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_MAX_ATTEMPTS", "Maximum verification attempts reached. Please request a new code.");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Invalid verification code.");
        }

        // Success: consume OTP (single use)
        otp.setConsumedAt(Instant.now());
        otpRepository.save(otp);

        // If purpose is EMAIL_VERIFICATION, mark user email as verified
        if ("EMAIL_VERIFICATION".equalsIgnoreCase(purpose)) {
            userRepository.findByEmail(email).ifPresent(user -> {
                user.setEmailVerified(true);
                userRepository.save(user);
            });
        }

        return new OtpResponse(true, "Email verified successfully.", 0);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        int deleted = otpRepository.deleteOldOtps(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} expired/consumed OTP records older than 24 hours", deleted);
        }
    }

    public String computeHmac(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC hash", e);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Email cannot be empty");
        }
        return email.trim().toLowerCase();
    }
}
