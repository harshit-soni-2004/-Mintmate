package com.spendsense.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"dev", "test"})
public class StubEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(StubEmailService.class);

    @Override
    public void sendVerificationOtp(String toEmail, String otpCode) {
        log.info("[DEV/TEST EMAIL STUB] Verification code for {}: {}", toEmail, otpCode);
    }
}
