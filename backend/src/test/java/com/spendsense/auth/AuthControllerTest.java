package com.spendsense.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spendsense.auth.dto.ForgotPasswordRequest;
import com.spendsense.auth.dto.OtpResponse;
import com.spendsense.auth.dto.ResetPasswordRequest;
import com.spendsense.auth.dto.SendOtpRequest;
import com.spendsense.auth.otp.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private OtpService otpService;

    @Test
    void sendOtpIgnoresSpoofedXForwardedForByDefault() {
        // trustForwardedHeaders is false by default
        AuthController controller = new AuthController(authService, otpService, false);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(otpService.sendVerificationOtp(eq("user@example.com"), eq("EMAIL_VERIFICATION"), eq("192.168.1.100")))
                .thenReturn(new OtpResponse(true, "Sent", 60));

        SendOtpRequest request = new SendOtpRequest("user@example.com", "EMAIL_VERIFICATION");
        var response = controller.sendOtp(request, httpRequest);

        assertThat(response.data().isSuccess()).isTrue();
        // Verify it passed the servlet remote address, NOT reading or using the forwarded header
        verify(otpService).sendVerificationOtp("user@example.com", "EMAIL_VERIFICATION", "192.168.1.100");
    }

    @Test
    void sendOtpHonorsXForwardedForWhenPropertyIsEnabled() {
        // trustForwardedHeaders is true
        AuthController controller = new AuthController(authService, otpService, true);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 10.0.0.1");
        when(otpService.sendVerificationOtp(eq("user@example.com"), eq("EMAIL_VERIFICATION"), eq("203.0.113.195")))
                .thenReturn(new OtpResponse(true, "Sent", 60));

        SendOtpRequest request = new SendOtpRequest("user@example.com", "EMAIL_VERIFICATION");
        var response = controller.sendOtp(request, httpRequest);

        assertThat(response.data().isSuccess()).isTrue();
        // Verify it parsed and passed the first client IP from the forwarded header
        verify(otpService).sendVerificationOtp("user@example.com", "EMAIL_VERIFICATION", "203.0.113.195");
    }

    @Test
    void forgotPasswordUsesRemoteAddrWhenForwardingDisabled() {
        AuthController controller = new AuthController(authService, otpService, false);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(authService.requestPasswordReset("user@example.com", "192.168.1.100"))
                .thenReturn(new OtpResponse(true, "Sent", 60));

        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");
        var response = controller.forgotPassword(request, httpRequest);

        assertThat(response.data().isSuccess()).isTrue();
        verify(authService).requestPasswordReset("user@example.com", "192.168.1.100");
    }

    @Test
    void resetPasswordDelegatesToAuthService() {
        AuthController controller = new AuthController(authService, otpService, false);
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "123456", "NewSecurePassword1!");
        when(authService.resetPassword(request)).thenReturn(new OtpResponse(true, "Reset successful", 0));

        var response = controller.resetPassword(request);

        assertThat(response.data().isSuccess()).isTrue();
        verify(authService).resetPassword(request);
    }
}
