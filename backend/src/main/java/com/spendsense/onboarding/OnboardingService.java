package com.spendsense.onboarding;

import com.spendsense.budget.Budget;
import com.spendsense.budget.BudgetPeriod;
import com.spendsense.budget.BudgetRepository;
import com.spendsense.common.exception.ApiException;
import com.spendsense.goal.Goal;
import com.spendsense.goal.GoalRepository;
import com.spendsense.onboarding.dto.CategoryAllocationDto;
import com.spendsense.onboarding.dto.IncomeSourceDto;
import com.spendsense.onboarding.dto.OnboardingRequest;
import com.spendsense.onboarding.dto.OnboardingResponse;
import com.spendsense.security.CurrentUser;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

    private final UserFinancialProfileRepository profileRepository;
    private final UserIncomeSourceRepository incomeSourceRepository;
    private final BaselineCategoryAllocationRepository allocationRepository;
    private final BudgetRepository budgetRepository;
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public OnboardingService(UserFinancialProfileRepository profileRepository,
                             UserIncomeSourceRepository incomeSourceRepository,
                             BaselineCategoryAllocationRepository allocationRepository,
                             BudgetRepository budgetRepository,
                             GoalRepository goalRepository,
                             UserRepository userRepository,
                             CurrentUser currentUser) {
        this.profileRepository = profileRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.allocationRepository = allocationRepository;
        this.budgetRepository = budgetRepository;
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public OnboardingResponse completeOnboarding(OnboardingRequest request) {
        if (request == null || request.persona() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ONBOARDING_REQUEST", "Persona is required");
        }

        UUID userId = currentUser.require().getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        // 1. Save / Update UserFinancialProfile
        UserFinancialProfile profile = profileRepository.findByUser_Id(userId)
                .orElseGet(() -> new UserFinancialProfile(user, request.persona(), request.monthlySavingsTarget(), request.monthlyInvestmentTarget()));

        profile.setPersona(request.persona());
        profile.setMonthlySavingsTarget(request.monthlySavingsTarget());
        profile.setMonthlyInvestmentTarget(request.monthlyInvestmentTarget());
        profile.setOnboardingCompleted(true);
        profile = profileRepository.save(profile);

        // 2. Save Income Sources
        List<UserIncomeSource> existingSources = incomeSourceRepository.findByUser_Id(userId);
        incomeSourceRepository.deleteAll(existingSources);

        List<IncomeSourceDto> savedSourceDtos = new ArrayList<>();
        if (request.incomeSources() != null) {
            for (IncomeSourceDto srcDto : request.incomeSources()) {
                if (srcDto.declaredAmount() != null && srcDto.declaredAmount().compareTo(BigDecimal.ZERO) > 0) {
                    UserIncomeSource source = new UserIncomeSource(
                            user,
                            srcDto.sourceName() != null ? srcDto.sourceName().trim() : "Primary Income",
                            srcDto.incomeType() != null ? srcDto.incomeType() : IncomeType.SALARY,
                            srcDto.frequency() != null ? srcDto.frequency() : IncomeFrequency.MONTHLY,
                            srcDto.declaredAmount(),
                            srcDto.expectedDayOfMonth(),
                            srcDto.employerOrPayerKeyword() != null ? srcDto.employerOrPayerKeyword().trim() : null,
                            srcDto.primary(),
                            srcDto.variable()
                    );
                    incomeSourceRepository.save(source);
                    savedSourceDtos.add(srcDto);
                }
            }
        }

        // 3. Save Baseline Category Allocations & Auto-seed Budgets
        List<BaselineCategoryAllocation> existingAllocs = allocationRepository.findByUser_Id(userId);
        allocationRepository.deleteAll(existingAllocs);

        List<CategoryAllocationDto> savedAllocDtos = new ArrayList<>();
        if (request.categoryAllocations() != null) {
            for (CategoryAllocationDto allocDto : request.categoryAllocations()) {
                if (allocDto.category() != null && allocDto.estimatedAmount() != null && allocDto.estimatedAmount().compareTo(BigDecimal.ZERO) > 0) {
                    BaselineCategoryAllocation alloc = new BaselineCategoryAllocation(
                            user,
                            allocDto.category(),
                            allocDto.estimatedAmount()
                    );
                    allocationRepository.save(alloc);
                    savedAllocDtos.add(allocDto);

                    // Seed draft monthly budget if user doesn't already have a budget for this category
                    boolean hasBudget = budgetRepository.existsByUser_IdAndCategory(userId, allocDto.category());
                    if (!hasBudget) {
                        Budget budget = new Budget(
                                user,
                                allocDto.category(),
                                allocDto.estimatedAmount(),
                                BudgetPeriod.MONTHLY,
                                LocalDate.now().withDayOfMonth(1),
                                null
                        );
                        budgetRepository.save(budget);
                    }
                }
            }
        }

        // 4. Seed Goals if declared
        if (request.monthlySavingsTarget() != null && request.monthlySavingsTarget().compareTo(BigDecimal.ZERO) > 0) {
            boolean hasGoal = goalRepository.existsByUser_IdAndNameIgnoreCase(userId, "Monthly Savings Target");
            if (!hasGoal) {
                Goal savingsGoal = new Goal(user, "Monthly Savings Target", request.monthlySavingsTarget(), BigDecimal.ZERO, LocalDate.now().plusMonths(12));
                goalRepository.save(savingsGoal);
            }
        }

        if (request.monthlyInvestmentTarget() != null && request.monthlyInvestmentTarget().compareTo(BigDecimal.ZERO) > 0) {
            boolean hasGoal = goalRepository.existsByUser_IdAndNameIgnoreCase(userId, "Monthly Investment Target");
            if (!hasGoal) {
                Goal investGoal = new Goal(user, "Monthly Investment Target", request.monthlyInvestmentTarget(), BigDecimal.ZERO, LocalDate.now().plusMonths(12));
                goalRepository.save(investGoal);
            }
        }

        return new OnboardingResponse(
                profile.getId(),
                profile.getPersona(),
                profile.isOnboardingCompleted(),
                profile.getMonthlySavingsTarget(),
                profile.getMonthlyInvestmentTarget(),
                savedSourceDtos,
                savedAllocDtos
        );
    }

    @Transactional(readOnly = true)
    public Optional<OnboardingResponse> getProfile() {
        UUID userId = currentUser.require().getUserId();
        return profileRepository.findByUser_Id(userId).map(profile -> {
            List<IncomeSourceDto> sourceDtos = incomeSourceRepository.findByUser_Id(userId).stream()
                    .map(s -> new IncomeSourceDto(
                            s.getSourceName(),
                            s.getIncomeType(),
                            s.getFrequency(),
                            s.getDeclaredAmount(),
                            s.getExpectedDayOfMonth(),
                            s.getEmployerOrPayerKeyword(),
                            s.isPrimary(),
                            s.isVariable()
                    )).toList();

            List<CategoryAllocationDto> allocDtos = allocationRepository.findByUser_Id(userId).stream()
                    .map(a -> new CategoryAllocationDto(a.getCategory(), a.getEstimatedAmount()))
                    .toList();

            return new OnboardingResponse(
                    profile.getId(),
                    profile.getPersona(),
                    profile.isOnboardingCompleted(),
                    profile.getMonthlySavingsTarget(),
                    profile.getMonthlyInvestmentTarget(),
                    sourceDtos,
                    allocDtos
            );
        });
    }
}
