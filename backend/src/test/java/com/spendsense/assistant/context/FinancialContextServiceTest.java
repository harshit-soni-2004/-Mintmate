package com.spendsense.assistant.context;

import com.spendsense.analytics.AggregationPeriod;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.TrendDirection;
import com.spendsense.analytics.dto.*;
import com.spendsense.assistant.context.model.*;
import com.spendsense.assistant.dto.BudgetRecommendationResponse;
import com.spendsense.assistant.dto.CategoryInsightsResponse;
import com.spendsense.assistant.dto.RecurringExpenseItem;
import com.spendsense.assistant.dto.SavingsTargetRecommendationResponse;
import com.spendsense.assistant.dto.SpendingFlexibilityResponse;
import com.spendsense.budget.BudgetPeriod;
import com.spendsense.budget.BudgetService;
import com.spendsense.budget.BudgetStatus;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.goal.GoalService;
import com.spendsense.goal.dto.GoalResponse;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialContextServiceTest {

    @Mock private AnalyticsService analyticsService;
    @Mock private BudgetService budgetService;
    @Mock private GoalService goalService;
    @Mock private TransactionRepository transactionRepository;

    private FinancialContextService contextService;
    private SpendSenseUserPrincipal userPrincipal;
    private User user;

    @BeforeEach
    void setUp() {
        contextService = new FinancialContextService(
                analyticsService,
                budgetService,
                goalService,
                transactionRepository
        );

        user = new User("alice@example.com", "hash", "Alice", "Asia/Kolkata");
        userPrincipal = new SpendSenseUserPrincipal(user);

        when(analyticsService.getTrends(any(), any(), any()))
                .thenReturn(new SpendingTrendResponse(AggregationPeriod.MONTHLY, Collections.emptyList(), LocalDate.now().minusMonths(6), LocalDate.now()));
        when(analyticsService.getCategoryBreakdown(any(), any()))
                .thenReturn(new CategoryBreakdownResponse(BigDecimal.ZERO, Collections.emptyList(), null));
        when(budgetService.list(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(goalService.list())
                .thenReturn(Collections.emptyList());
    }

    @Nested
    @DisplayName("Income Profile Tests")
    class IncomeProfileTests {

        @Test
        @DisplayName("Stable income over 6 months produces HIGH stability and HIGH sufficiency")
        void stableIncomeCalculation() {
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.EXPENSE), any(), any()))
                    .thenReturn(30L);
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.INCOME), any(), any()))
                    .thenReturn(6L);

            SpendingTrendResponse trendResponse = new SpendingTrendResponse(
                    AggregationPeriod.MONTHLY,
                    List.of(
                            new SpendingTrendPoint("2026-04", LocalDate.of(2026, 4, 1), new BigDecimal("40000"), new BigDecimal("60000"), 10L),
                            new SpendingTrendPoint("2026-05", LocalDate.of(2026, 5, 1), new BigDecimal("41000"), new BigDecimal("60000"), 10L),
                            new SpendingTrendPoint("2026-06", LocalDate.of(2026, 6, 1), new BigDecimal("39000"), new BigDecimal("60000"), 10L),
                            new SpendingTrendPoint("2026-07", LocalDate.of(2026, 7, 1), new BigDecimal("40500"), new BigDecimal("60500"), 10L),
                            new SpendingTrendPoint("2026-08", LocalDate.of(2026, 8, 1), new BigDecimal("42000"), new BigDecimal("60000"), 10L),
                            new SpendingTrendPoint("2026-09", LocalDate.of(2026, 9, 1), new BigDecimal("40000"), new BigDecimal("60000"), 10L)
                    ),
                    LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 9, 30)
            );
            when(analyticsService.getTrends(any(), any(), any())).thenReturn(trendResponse);

            FinancialProfile profile = contextService.computeFinancialProfile(userPrincipal, 6);

            assertThat(profile.dataSufficiency().level()).isEqualTo(DataSufficiency.HIGH);
            assertThat(profile.income().averageMonthlyIncome()).isEqualByComparingTo("60083.33");
            assertThat(profile.income().stability()).isEqualTo("HIGH");
            assertThat(profile.income().trend()).isEqualTo(TrendDirection.UNCHANGED);
        }

        @Test
        @DisplayName("Variable income produces MEDIUM or LOW stability")
        void variableIncomeCalculation() {
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.EXPENSE), any(), any()))
                    .thenReturn(25L);
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.INCOME), any(), any()))
                    .thenReturn(4L);

            SpendingTrendResponse trendResponse = new SpendingTrendResponse(
                    AggregationPeriod.MONTHLY,
                    List.of(
                            new SpendingTrendPoint("2026-04", LocalDate.of(2026, 4, 1), new BigDecimal("30000"), new BigDecimal("40000"), 5L),
                            new SpendingTrendPoint("2026-05", LocalDate.of(2026, 5, 1), new BigDecimal("30000"), new BigDecimal("90000"), 5L),
                            new SpendingTrendPoint("2026-06", LocalDate.of(2026, 6, 1), new BigDecimal("30000"), new BigDecimal("30000"), 5L),
                            new SpendingTrendPoint("2026-07", LocalDate.of(2026, 7, 1), new BigDecimal("30000"), new BigDecimal("110000"), 5L)
                    ),
                    LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 7, 31)
            );
            when(analyticsService.getTrends(any(), any(), any())).thenReturn(trendResponse);

            FinancialProfile profile = contextService.computeFinancialProfile(userPrincipal, 4);

            assertThat(profile.income().stability()).isIn("MEDIUM", "LOW");
        }

        @Test
        @DisplayName("Insufficient history results in LOW data sufficiency")
        void insufficientHistory() {
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.EXPENSE), any(), any()))
                    .thenReturn(3L);
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.INCOME), any(), any()))
                    .thenReturn(1L);

            FinancialProfile profile = contextService.computeFinancialProfile(userPrincipal, 1);

            assertThat(profile.dataSufficiency().level()).isEqualTo(DataSufficiency.LOW);
            assertThat(profile.dataSufficiency().explanation()).contains("Limited history");
        }
    }

    @Nested
    @DisplayName("Spending & Category Profile Tests")
    class SpendingAndCategoryProfileTests {

        @Test
        @DisplayName("Category profile properly calculates monthly average and identifies volatility")
        void categoryProfileMetrics() {
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.EXPENSE), any(), any()))
                    .thenReturn(30L);
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.INCOME), any(), any()))
                    .thenReturn(5L);

            CategoryBreakdownResponse breakdown = new CategoryBreakdownResponse(
                    new BigDecimal("60000.00"),
                    List.of(
                            new CategoryAnalysis(TransactionCategory.FOOD, new BigDecimal("30000.00"), new BigDecimal("50.0"), new BigDecimal("20000.00"), new BigDecimal("10000.00"), new BigDecimal("50.0"), TrendDirection.INCREASED, 15L),
                            new CategoryAnalysis(TransactionCategory.SHOPPING, new BigDecimal("18000.00"), new BigDecimal("30.0"), new BigDecimal("20000.00"), new BigDecimal("-2000.00"), new BigDecimal("-10.0"), TrendDirection.DECREASED, 10L),
                            new CategoryAnalysis(TransactionCategory.TRAVEL, new BigDecimal("12000.00"), new BigDecimal("20.0"), new BigDecimal("12000.00"), BigDecimal.ZERO, BigDecimal.ZERO, TrendDirection.UNCHANGED, 5L)
                    ),
                    TransactionCategory.FOOD
            );
            when(analyticsService.getCategoryBreakdown(any(), any())).thenReturn(breakdown);

            FinancialProfile profile = contextService.computeFinancialProfile(userPrincipal, 6);

            assertThat(profile.categories()).hasSize(3);
            CategoryProfile food = profile.categories().stream().filter(c -> c.category() == TransactionCategory.FOOD).findFirst().orElseThrow();
            assertThat(food.averageMonthlySpend()).isEqualByComparingTo("5000.00");
            assertThat(food.volatility()).isEqualTo("HIGH"); // 50% change is > 25% threshold
            assertThat(food.behavioralSummary()).contains("Food & Dining spending averaged");
        }

        @Test
        @DisplayName("Identifies highest spend, most volatile, and fastest growing in CategoryInsights")
        void categoryInsightsAnalysis() {
            CategoryBreakdownResponse breakdown = new CategoryBreakdownResponse(
                    new BigDecimal("50000.00"),
                    List.of(
                            new CategoryAnalysis(TransactionCategory.FOOD, new BigDecimal("25000.00"), new BigDecimal("50.0"), new BigDecimal("20000.00"), new BigDecimal("5000.00"), new BigDecimal("25.0"), TrendDirection.INCREASED, 12L),
                            new CategoryAnalysis(TransactionCategory.TRAVEL, new BigDecimal("15000.00"), new BigDecimal("30.0"), new BigDecimal("8000.00"), new BigDecimal("7000.00"), new BigDecimal("87.5"), TrendDirection.INCREASED, 8L),
                            new CategoryAnalysis(TransactionCategory.ENTERTAINMENT, new BigDecimal("10000.00"), new BigDecimal("20.0"), new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, TrendDirection.UNCHANGED, 4L)
                    ),
                    TransactionCategory.FOOD
            );
            when(analyticsService.getCategoryBreakdown(any(), any())).thenReturn(breakdown);

            CategoryInsightsResponse insights = contextService.getCategoryInsights(userPrincipal, 6);

            assertThat(insights.highestSpendCategory()).isEqualTo("Food & Dining");
            assertThat(insights.mostVolatileCategory()).isEqualTo("Travel & Commute");
            assertThat(insights.fastestGrowingCategory()).isIn("Food & Dining", "Travel & Commute");
            assertThat(insights.insights()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Budget Recommendation Tests")
    class BudgetRecommendationTests {

        @Test
        @DisplayName("Generates deterministic weighted recommendation when sufficient category history exists")
        void budgetRecommendationWithHistory() {
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.EXPENSE), any(), any()))
                    .thenReturn(30L);
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.INCOME), any(), any()))
                    .thenReturn(6L);

            CategoryBreakdownResponse breakdown = new CategoryBreakdownResponse(
                    new BigDecimal("50000.00"),
                    List.of(new CategoryAnalysis(TransactionCategory.FOOD, new BigDecimal("48000.00"), new BigDecimal("96.0"), new BigDecimal("40000.00"), new BigDecimal("8000.00"), new BigDecimal("20.0"), TrendDirection.INCREASED, 20L)),
                    TransactionCategory.FOOD
            );
            when(analyticsService.getCategoryBreakdown(any(), any())).thenReturn(breakdown);

            // Active budget exists
            BudgetResponse foodBudget = new BudgetResponse(
                    UUID.randomUUID(),
                    TransactionCategory.FOOD,
                    new BigDecimal("9000.00"),
                    BudgetPeriod.MONTHLY,
                    LocalDate.now().withDayOfMonth(1),
                    LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),
                    new BigDecimal("5000.00"),
                    new BigDecimal("4000.00"),
                    55.5,
                    15,
                    BudgetStatus.ON_TRACK,
                    Instant.now(), Instant.now()
            );
            when(budgetService.list(any(), any(), any())).thenReturn(List.of(foodBudget));

            BudgetRecommendationResponse rec = contextService.calculateBudgetRecommendation(userPrincipal, TransactionCategory.FOOD, 6);

            // 6-month total 48,000 => histAvg = 8,000. recentAvg = 48,000 (total slice)
            assertThat(rec.category()).isEqualTo(TransactionCategory.FOOD);
            assertThat(rec.recommendedBudget()).isGreaterThan(BigDecimal.ZERO);
            assertThat(rec.recommendedRangeMin()).isLessThanOrEqualTo(rec.recommendedBudget());
            assertThat(rec.recommendedRangeMax()).isGreaterThanOrEqualTo(rec.recommendedBudget());
            assertThat(rec.existingBudgetLimit()).isEqualByComparingTo("9000.00");
            assertThat(rec.supportingFacts()).anyMatch(fact -> fact.contains("Existing active budget limit for Food & Dining is"));
        }

        @Test
        @DisplayName("Provides baseline recommendation when category has no history")
        void budgetRecommendationWithoutHistory() {
            when(analyticsService.getCategoryBreakdown(any(), any())).thenReturn(new CategoryBreakdownResponse(BigDecimal.ZERO, List.of(), null));

            BudgetRecommendationResponse rec = contextService.calculateBudgetRecommendation(userPrincipal, TransactionCategory.EDUCATION, 6);

            assertThat(rec.category()).isEqualTo(TransactionCategory.EDUCATION);
            assertThat(rec.confidence()).isEqualTo("LOW");
            assertThat(rec.recommendedBudget()).isEqualByComparingTo("3000.00");
            assertThat(rec.supportingFacts()).anyMatch(fact -> fact.contains("No previous transaction activity found"));
        }
    }

    @Nested
    @DisplayName("Savings Recommendation & Goal Tests")
    class SavingsRecommendationTests {

        @Test
        @DisplayName("Recommends savings target aligned with active goal deadlines")
        void savingsRecommendationWithGoal() {
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.EXPENSE), any(), any()))
                    .thenReturn(30L);
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.INCOME), any(), any()))
                    .thenReturn(6L);

            SpendingTrendResponse trendResponse = new SpendingTrendResponse(
                    AggregationPeriod.MONTHLY,
                    List.of(
                            new SpendingTrendPoint("2026-08", LocalDate.of(2026, 8, 1), new BigDecimal("40000"), new BigDecimal("80000"), 15L),
                            new SpendingTrendPoint("2026-09", LocalDate.of(2026, 9, 1), new BigDecimal("40000"), new BigDecimal("80000"), 15L)
                    ),
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 9, 30)
            );
            when(analyticsService.getTrends(any(), any(), any())).thenReturn(trendResponse);

            // Goal needing ₹10,000/month
            GoalResponse goal = new GoalResponse(
                    UUID.randomUUID(),
                    "Car Downpayment",
                    new BigDecimal("120000.00"),
                    new BigDecimal("20000.00"),
                    new BigDecimal("100000.00"),
                    16.6,
                    false,
                    LocalDate.now().plusMonths(10),
                    Instant.now(),
                    Instant.now()
            );
            when(goalService.list()).thenReturn(List.of(goal));

            SavingsTargetRecommendationResponse rec = contextService.calculateSavingsRecommendation(userPrincipal, 6);

            assertThat(rec.recommendedMonthlySavings()).isGreaterThanOrEqualTo(new BigDecimal("10000.00"));
            assertThat(rec.supportingFacts()).anyMatch(f -> f.contains("Active savings goals require"));
        }

        @Test
        @DisplayName("Savings target defaults to conservative rate under low data sufficiency")
        void savingsRecommendationLowSufficiency() {
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), any(), any(), any()))
                    .thenReturn(2L);

            SpendingTrendResponse trendResponse = new SpendingTrendResponse(
                    AggregationPeriod.MONTHLY,
                    List.of(new SpendingTrendPoint("2026-09", LocalDate.of(2026, 9, 1), new BigDecimal("20000"), new BigDecimal("50000"), 2L)),
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 30)
            );
            when(analyticsService.getTrends(any(), any(), any())).thenReturn(trendResponse);

            SavingsTargetRecommendationResponse rec = contextService.calculateSavingsRecommendation(userPrincipal, 1);

            assertThat(rec.confidence()).isEqualTo("LOW");
            assertThat(rec.recommendedSavingsRate()).isEqualByComparingTo("10.0");
        }
    }

    @Nested
    @DisplayName("Spending Flexibility Tests")
    class SpendingFlexibilityTests {

        @Test
        @DisplayName("Spending flexibility accurately partitions income into commitments, living expenses, and discretionary")
        void spendingFlexibilityBreakdown() {
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.EXPENSE), any(), any()))
                    .thenReturn(40L);
            when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), eq(TransactionType.INCOME), any(), any()))
                    .thenReturn(6L);

            SpendingTrendResponse trendResponse = new SpendingTrendResponse(
                    AggregationPeriod.MONTHLY,
                    List.of(
                            new SpendingTrendPoint("2026-08", LocalDate.of(2026, 8, 1), new BigDecimal("50000"), new BigDecimal("100000"), 20L),
                            new SpendingTrendPoint("2026-09", LocalDate.of(2026, 9, 1), new BigDecimal("50000"), new BigDecimal("100000"), 20L)
                    ),
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 9, 30)
            );
            when(analyticsService.getTrends(any(), any(), any())).thenReturn(trendResponse);

            // Recurring transactions: Gym (1,500) and Broadband (1,000)
            Transaction gym1 = new Transaction(user, new BigDecimal("1500.00"), "Cult.fit", TransactionCategory.HEALTH, TransactionType.EXPENSE, LocalDate.now().minusMonths(1), null);
            Transaction gym2 = new Transaction(user, new BigDecimal("1500.00"), "Cult.fit", TransactionCategory.HEALTH, TransactionType.EXPENSE, LocalDate.now().minusMonths(2), null);
            Transaction bb1 = new Transaction(user, new BigDecimal("1000.00"), "Airtel Fiber", TransactionCategory.BILLS, TransactionType.EXPENSE, LocalDate.now().minusMonths(1), null);
            Transaction bb2 = new Transaction(user, new BigDecimal("1000.00"), "Airtel Fiber", TransactionCategory.BILLS, TransactionType.EXPENSE, LocalDate.now().minusMonths(2), null);

            when(transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(any(), eq(TransactionType.EXPENSE), any(), any()))
                    .thenReturn(List.of(gym1, gym2, bb1, bb2));

            SpendingFlexibilityResponse flex = contextService.getSpendingFlexibility(userPrincipal, 6);

            assertThat(flex.monthlyIncome()).isEqualByComparingTo("100000.00");
            assertThat(flex.committedMonthlyExpenses()).isEqualByComparingTo("2500.00"); // 1500 + 1000
            assertThat(flex.typicalBaselineExpenses()).isEqualByComparingTo("47500.00"); // 50000 - 2500
            assertThat(flex.availableDiscretionaryCapacity()).isEqualByComparingTo("50000.00"); // 100000 - 2500 - 47500
            assertThat(flex.committedPercentage()).isEqualByComparingTo("2.5");
        }
    }

    @Nested
    @DisplayName("Recurring Commitments Tests")
    class RecurringCommitmentsTests {

        @Test
        @DisplayName("Distinguishes HIGH vs MEDIUM confidence recurring commitments based on repetition count")
        void recurringConfidenceLevels() {
            Transaction sub1 = new Transaction(user, new BigDecimal("499.00"), "Netflix", TransactionCategory.ENTERTAINMENT, TransactionType.EXPENSE, LocalDate.now().minusMonths(1), null);
            Transaction sub2 = new Transaction(user, new BigDecimal("499.00"), "Netflix", TransactionCategory.ENTERTAINMENT, TransactionType.EXPENSE, LocalDate.now().minusMonths(2), null);
            Transaction sub3 = new Transaction(user, new BigDecimal("499.00"), "Netflix", TransactionCategory.ENTERTAINMENT, TransactionType.EXPENSE, LocalDate.now().minusMonths(3), null);

            Transaction rent1 = new Transaction(user, new BigDecimal("25000.00"), "Landlord", TransactionCategory.BILLS, TransactionType.EXPENSE, LocalDate.now().minusMonths(1), null);
            Transaction rent2 = new Transaction(user, new BigDecimal("25000.00"), "Landlord", TransactionCategory.BILLS, TransactionType.EXPENSE, LocalDate.now().minusMonths(2), null);

            when(transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(any(), eq(TransactionType.EXPENSE), any(), any()))
                    .thenReturn(List.of(rent1, rent2, sub1, sub2, sub3));

            FinancialProfile profile = contextService.computeFinancialProfile(userPrincipal, 6);

            RecurringCommitmentProfile recurring = profile.recurring();
            assertThat(recurring.totalRecurringCount()).isEqualTo(2);

            RecurringExpenseItem netflix = recurring.commitments().stream().filter(i -> i.merchant().equalsIgnoreCase("Netflix")).findFirst().orElseThrow();
            assertThat(netflix.confidence()).isEqualTo("HIGH"); // 3 repeats

            RecurringExpenseItem landlord = recurring.commitments().stream().filter(i -> i.merchant().equalsIgnoreCase("Landlord")).findFirst().orElseThrow();
            assertThat(landlord.confidence()).isEqualTo("MEDIUM"); // 2 repeats
        }
    }
}
