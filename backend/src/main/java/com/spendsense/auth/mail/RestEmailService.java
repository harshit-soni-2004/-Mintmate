package com.spendsense.auth.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional emails via HTTPS (Port 443) using Brevo or Resend REST APIs.
 * This completely bypasses cloud firewall restrictions on outbound SMTP ports (25, 465, 587)
 * such as Render's Free tier firewall.
 */
@Service
@Primary
@ConditionalOnProperty(name = "spendsense.mail.api-key")
public class RestEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(RestEmailService.class);

    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final String provider;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RestEmailService(
            @Value("${spendsense.mail.api-key:${BREVO_API_KEY:${RESEND_API_KEY:}}}") String apiKey,
            @Value("${spendsense.mail.from:${SPRING_MAIL_USERNAME:iharshsoni@gmail.com}}") String fromEmail,
            @Value("${spendsense.mail.from-name:Mintmate}") String fromName,
            @Value("${spendsense.mail.provider:auto}") String provider,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.fromEmail = fromEmail != null ? fromEmail.trim() : "iharshsoni@gmail.com";
        this.fromName = fromName != null ? fromName.trim() : "Mintmate";
        this.provider = resolveProvider(this.apiKey, provider);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("Initialized REST Email Service with provider: {}", this.provider);
    }

    private String resolveProvider(String key, String configuredProvider) {
        if ("resend".equalsIgnoreCase(configuredProvider) || key.startsWith("re_")) {
            return "resend";
        }
        return "brevo";
    }

    @Override
    public void sendVerificationOtp(String toEmail, String otpCode) {
        if (apiKey.isEmpty()) {
            log.warn("No email API key configured. Cannot dispatch email to {}", toEmail);
            log.info("[BACKUP OTP LOG for {}]: {}", toEmail, otpCode);
            return;
        }

        try {
            if ("resend".equalsIgnoreCase(provider)) {
                sendViaResend(toEmail, otpCode);
            } else {
                sendViaBrevo(toEmail, otpCode);
            }
            log.info("Successfully dispatched verification email via {} REST API to {}", provider, toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email via REST API to {}: {}", toEmail, e.getMessage());
            log.warn("[FALLBACK OTP LOG for {}]: {}", toEmail, otpCode);
        }
    }

    private void sendViaBrevo(String toEmail, String otpCode) throws Exception {
        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("email", toEmail)),
                "subject", "Mintmate Verification Code: " + otpCode,
                "htmlContent", buildHtmlBody(otpCode)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Brevo API error HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private void sendViaResend(String toEmail, String otpCode) throws Exception {
        Map<String, Object> payload = Map.of(
                "from", fromName + " <" + (fromEmail.contains("@") ? fromEmail : "onboarding@resend.dev") + ">",
                "to", List.of(toEmail),
                "subject", "Mintmate Verification Code: " + otpCode,
                "html", buildHtmlBody(otpCode)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Resend API error HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private String buildHtmlBody(String otpCode) {
        return "<div style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 520px; margin: 0 auto; padding: 32px 24px; background: #0a1914; color: #ecfdf5; border-radius: 16px;\">" +
                "<div style=\"margin-bottom: 24px;\"><span style=\"font-size: 20px; font-weight: 700; color: #34d399;\">✦ Mintmate</span></div>" +
                "<h1 style=\"font-size: 22px; margin-bottom: 12px; color: #ffffff;\">Verify Your Email</h1>" +
                "<p style=\"color: #a7f3d0; line-height: 1.5; font-size: 15px;\">Please enter the 6-digit verification code below to activate your account:</p>" +
                "<div style=\"background: #064e3b; padding: 16px 24px; border-radius: 12px; text-align: center; margin: 28px 0; letter-spacing: 8px; font-size: 32px; font-weight: 800; color: #a7f3d0;\">" +
                otpCode +
                "</div>" +
                "<p style=\"color: #6ee7b7; font-size: 13px; margin: 0;\">This code expires in 10 minutes. If you did not request this, you can safely ignore this email.</p>" +
                "</div>";
    }
}
