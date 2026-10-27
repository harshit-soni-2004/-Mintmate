package com.spendsense.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.budget.Budget;
import com.spendsense.budget.BudgetRepository;
import com.spendsense.goal.Goal;
import com.spendsense.goal.GoalRepository;
import com.spendsense.onboarding.dto.CategoryAllocationDto;
import com.spendsense.onboarding.dto.IncomeSourceDto;
import com.spendsense.onboarding.dto.OnboardingRequest;
import com.spendsense.onboarding.dto.OnboardingResponse;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock private UserFinancialProfileRepository profileRepository;
    @Mock private UserIncomeSourceRepository incomeSourceRepository;
    @Mock private BaselineCategoryAllocationRepository allocationRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;

    private OnboardingService onboardingService;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("harshit@example.com", "hash", "Harshit", "Asia/Kolkata");
        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        onboardingService = new OnboardingService(
                profileRepository, incomeSourceRepository, allocationRepository,
                budgetRepository, goalRepository, userRepository, currentUser
        );
    }

    @Test
    @DisplayName("Complete onboarding: saves profile, sources, allocations and auto-seeds baseline budgets & goals")
    void testCompleteOnboarding() {
        OnboardingRequest request = new OnboardingRequest(
                UserPersona.SALARIED,
                new BigDecimal("20000.00"), // savings target
                new BigDecimal("10000.00"), // investment target
                List.of(new IncomeSourceDto(
                        "Primary Employer", IncomeType.SALARY, IncomeFrequency.MONTHLY,
                        new BigDecimal("75000.00"), 1, "ACME"
                )),
                List.of(
                        new CategoryAllocationDto(TransactionCategory.FOOD, new BigDecimal("12000.00")),
                        new CategoryAllocationDto(TransactionCategory.TRAVEL, new BigDecimal("5000.00"))
                )
        );

        when(profileRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());
        when(profileRepository.save(any(UserFinancialProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(budgetRepository.existsByUser_IdAndCategory(user.getId(), TransactionCategory.FOOD)).thenReturn(false);
        when(budgetRepository.existsByUser_IdAndCategory(user.getId(), TransactionCategory.TRAVEL)).thenReturn(false);
        when(goalRepository.existsByUser_IdAndNameIgnoreCase(user.getId(), "Monthly Savings Target")).thenReturn(false);
        when(goalRepository.existsByUser_IdAndNameIgnoreCase(user.getId(), "Monthly Investment Target")).thenReturn(false);

        OnboardingResponse response = onboardingService.completeOnboarding(request);

        assertThat(response.persona()).isEqualTo(UserPersona.SALARIED);
        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.monthlySavingsTarget()).isEqualByComparingTo("20000.00");
        assertThat(response.monthlyInvestmentTarget()).isEqualByComparingTo("10000.00");
        assertThat(response.incomeSources()).hasSize(1);
        assertThat(response.categoryAllocations()).hasSize(2);

        // Verify baseline budgets auto-seeded for FOOD and TRAVEL
        verify(budgetRepository, times(2)).save(any(Budget.class));
        // Verify goals auto-seeded for savings and investment
        verify(goalRepository, times(2)).save(any(Goal.class));
    }
}
