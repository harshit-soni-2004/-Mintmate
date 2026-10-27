package com.spendsense.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(EmailService.class)
public class StubEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(StubEmailService.class);

    @Override
    public void sendVerificationOtp(String toEmail, String otpCode) {
        log.info("[EMAIL STUB - NO SMTP CONFIGURED] Verification code for {}: {}", toEmail, otpCode);
    }
}
