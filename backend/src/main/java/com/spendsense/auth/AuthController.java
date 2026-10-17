package com.spendsense.auth;

import com.spendsense.auth.dto.AuthResponse;
import com.spendsense.auth.dto.GoogleLoginRequest;
import com.spendsense.auth.dto.LoginRequest;
import com.spendsense.auth.dto.RegisterRequest;
import com.spendsense.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/auth", "/api/auth"})
public class AuthController {

    private final AuthService authService;
    private final com.spendsense.auth.otp.OtpService otpService;
    private final boolean trustForwardedHeaders;

    public AuthController(AuthService authService) {
        this(authService, null, false);
    }

    public AuthController(AuthService authService, com.spendsense.auth.otp.OtpService otpService) {
        this(authService, otpService, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(
            AuthService authService,
            com.spendsense.auth.otp.OtpService otpService,
            @org.springframework.beans.factory.annotation.Value("${app.trust-forwarded-headers:false}") boolean trustForwardedHeaders
    ) {
        this.authService = authService;
        this.otpService = otpService;
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.of(authService.login(request));
    }

    @PostMapping("/google")
    public ApiResponse<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.of(authService.loginWithGoogle(request));
    }

    @PostMapping("/otp/send")
    public ApiResponse<com.spendsense.auth.dto.OtpResponse> sendOtp(
            @Valid @RequestBody com.spendsense.auth.dto.SendOtpRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        return ApiResponse.of(otpService.sendVerificationOtp(request.getEmail(), request.getPurpose(), clientIp));
    }

    @PostMapping("/otp/verify")
    public ApiResponse<com.spendsense.auth.dto.OtpResponse> verifyOtp(
            @Valid @RequestBody com.spendsense.auth.dto.VerifyOtpRequest request
    ) {
        return ApiResponse.of(otpService.verifyOtp(request.getEmail(), request.getCode(), request.getPurpose()));
    }

    @PostMapping("/password/forgot")
    public ApiResponse<com.spendsense.auth.dto.OtpResponse> forgotPassword(
            @Valid @RequestBody com.spendsense.auth.dto.ForgotPasswordRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        return ApiResponse.of(authService.requestPasswordReset(request.email(), clientIp));
    }

    @PostMapping("/password/reset")
    public ApiResponse<com.spendsense.auth.dto.OtpResponse> resetPassword(
            @Valid @RequestBody com.spendsense.auth.dto.ResetPasswordRequest request
    ) {
        return ApiResponse.of(authService.resetPassword(request));
    }

    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (trustForwardedHeaders) {
            String forwarded = httpRequest.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return httpRequest.getRemoteAddr();
    }
}
