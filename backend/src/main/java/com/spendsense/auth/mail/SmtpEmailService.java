package com.spendsense.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Override
    public void sendVerificationOtp(String toEmail, String otpCode) {
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured in prod profile. Email to {} could not be dispatched.", toEmail);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("SpendSense Verification Code");
            message.setText("Your verification code is: " + otpCode + "\n\nThis code expires in 10 minutes. If you did not request this, please ignore this email.");
            mailSender.send(message);
            // Never log the OTP code in prod profile!
            log.info("Dispatched verification email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }
}
