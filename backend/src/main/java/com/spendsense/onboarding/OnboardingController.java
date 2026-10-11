package com.spendsense.onboarding;

import com.spendsense.common.api.ApiResponse;
import com.spendsense.onboarding.dto.OnboardingRequest;
import com.spendsense.onboarding.dto.OnboardingResponse;
import com.spendsense.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final IncomeClassificationService incomeClassificationService;
    private final CurrentUser currentUser;

    public OnboardingController(OnboardingService onboardingService,
                                IncomeClassificationService incomeClassificationService,
                                CurrentUser currentUser) {
        this.onboardingService = onboardingService;
        this.incomeClassificationService = incomeClassificationService;
        this.currentUser = currentUser;
    }

    @PostMapping("/complete")
    public ApiResponse<OnboardingResponse> completeOnboarding(@Valid @RequestBody OnboardingRequest request) {
        OnboardingResponse response = onboardingService.completeOnboarding(request);
        return ApiResponse.of(response);
    }

    @GetMapping("/profile")
    public ApiResponse<OnboardingResponse> getProfile() {
        Optional<OnboardingResponse> profile = onboardingService.getProfile();
        return profile.map(ApiResponse::of).orElseGet(() -> ApiResponse.of(null));
    }

    @GetMapping("/missing-salary-alert")
    public ApiResponse<Map<String, String>> checkMissingSalaryAlert() {
        UUID userId = currentUser.require().getUserId();
        Optional<String> alert = incomeClassificationService.checkMissingSalaryCredit(userId, LocalDate.now());
        if (alert.isPresent()) {
            return ApiResponse.of(Map.of("hasAlert", "true", "message", alert.get()));
        }
        return ApiResponse.of(Map.of("hasAlert", "false"));
    }
}
