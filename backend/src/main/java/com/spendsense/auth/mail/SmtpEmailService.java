package com.spendsense.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spendsense.mail.from:${spring.mail.username:no-reply@mintmate.app}}")
    private String fromAddress;

    @Override
    public void sendVerificationOtp(String toEmail, String otpCode) {
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Email to {} could not be dispatched.", toEmail);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) {
                message.setFrom(fromAddress);
            }
            message.setTo(toEmail);
            message.setSubject("Mintmate Verification Code: " + otpCode);
            message.setText("Hello,\n\nYour Mintmate verification code is:\n\n    " + otpCode +
                    "\n\nThis code will expire in 10 minutes.\nIf you did not request this code, you can safely ignore this email.\n\nBest regards,\nThe Mintmate Team");
            mailSender.send(message);
            log.info("Successfully dispatched verification email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }
}
