package com.spendsense.auth.mail;

public interface EmailService {
    void sendVerificationOtp(String toEmail, String otpCode);
}
