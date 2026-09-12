package com.spendsense.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.FinancialInsight;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.analytics.dto.TrendMetric;
import com.spendsense.budget.Budget;
import com.spendsense.budget.BudgetPeriod;
import com.spendsense.budget.BudgetRepository;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock private AnalyticsService analyticsService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private CurrentUser currentUser;

    private User user;
    private InsightService insightService;

    @BeforeEach
    void setUp() {
        user = new User("priya@example.com", "hash", "Priya Sharma", "Asia/Kolkata");
        lenient().when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        insightService = new InsightService(analyticsService, transactionRepository, budgetRepository, currentUser);

        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        lenient().when(analyticsService.normalizeRange(any(), any())).thenReturn(new LocalDate[]{from, to});
    }

    @Test
    void generatesCategoryIncreaseAndDecreaseInsights() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        SpendingSummaryResponse summary = new SpendingSummaryResponse(
                new BigDecimal("50000"), new BigDecimal("20000"), new BigDecimal("30000"),
                new BigDecimal("60"), 10L, new BigDecimal("2000"), new BigDecimal("5000"), from, to);

        PeriodComparisonResponse comparison = new PeriodComparisonResponse(
                from, to, from.minusMonths(1), to.minusMonths(1),
                null, null, null, new BigDecimal("60"), new BigDecimal("60"), BigDecimal.ZERO);

        CategoryAnalysis food = new CategoryAnalysis(
                TransactionCategory.FOOD, new BigDecimal("8000"), new BigDecimal("40"),
                new BigDecimal("5000"), new BigDecimal("3000"), new BigDecimal("60.00"),
                TrendDirection.INCREASED, 8L);

        CategoryAnalysis travel = new CategoryAnalysis(
                TransactionCategory.TRAVEL, new BigDecimal("2000"), new BigDecimal("10"),
                new BigDecimal("3000"), new BigDecimal("-1000"), new BigDecimal("-33.33"),
                TrendDirection.DECREASED, 3L);

        CategoryBreakdownResponse categories = new CategoryBreakdownResponse(
                new BigDecimal("20000"), List.of(food, travel), TransactionCategory.FOOD);

        when(analyticsService.getSummary(from, to)).thenReturn(summary);
        when(analyticsService.getComparison(from, to)).thenReturn(comparison);
        when(analyticsService.getCategoryBreakdown(from, to)).thenReturn(categories);

        List<FinancialInsight> insights = insightService.generateInsights(from, to);

        assertThat(insights).anyMatch(i -> i.type() == InsightType.CATEGORY_INCREASE && i.category() == TransactionCategory.FOOD);
        assertThat(insights).anyMatch(i -> i.type() == InsightType.CATEGORY_DECREASE && i.category() == TransactionCategory.TRAVEL);
        assertThat(insights).anyMatch(i -> i.type() == InsightType.TOP_SPENDING_CATEGORY && i.category() == TransactionCategory.FOOD);
    }

    @Test
    void generatesBudgetWarningAndExceededInsights() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        SpendingSummaryResponse summary = new SpendingSummaryResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L, BigDecimal.ZERO, BigDecimal.ZERO, from, to);
        PeriodComparisonResponse comparison = new PeriodComparisonResponse(
                from, to, from.minusMonths(1), to.minusMonths(1),
                null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        CategoryBreakdownResponse categories = new CategoryBreakdownResponse(BigDecimal.ZERO, List.of(), null);

        when(analyticsService.getSummary(from, to)).thenReturn(summary);
        when(analyticsService.getComparison(from, to)).thenReturn(comparison);
        when(analyticsService.getCategoryBreakdown(from, to)).thenReturn(categories);

        Budget budgetExceeded = new Budget(user, TransactionCategory.SHOPPING, new BigDecimal("5000.00"),
                BudgetPeriod.MONTHLY, from, to);
        Budget budgetWarning = new Budget(user, TransactionCategory.FOOD, new BigDecimal("10000.00"),
                BudgetPeriod.MONTHLY, from, to);

        when(budgetRepository.findActiveBudgets(user.getId(), from, to))
                .thenReturn(List.of(budgetExceeded, budgetWarning));

        when(transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
                user.getId(), TransactionCategory.SHOPPING, TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("5500.00")); // 110% -> EXCEEDED
        when(transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
                user.getId(), TransactionCategory.FOOD, TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("8500.00")); // 85% -> WARNING

        List<FinancialInsight> insights = insightService.generateInsights(from, to);

        assertThat(insights).anyMatch(i -> i.type() == InsightType.BUDGET_EXCEEDED && i.severity() == InsightSeverity.CRITICAL);
        assertThat(insights).anyMatch(i -> i.type() == InsightType.BUDGET_WARNING && i.severity() == InsightSeverity.WARNING);
    }

    @Test
    void filtersOutTrivialChangesToAvoidNoise() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        SpendingSummaryResponse summary = new SpendingSummaryResponse(
                new BigDecimal("50000"), new BigDecimal("1000"), new BigDecimal("49000"),
                new BigDecimal("98"), 2L, new BigDecimal("500"), new BigDecimal("600"), from, to);
        PeriodComparisonResponse comparison = new PeriodComparisonResponse(
                from, to, from.minusMonths(1), to.minusMonths(1),
                null, null, null, new BigDecimal("98"), new BigDecimal("97"), new BigDecimal("1.00")); // only 1% diff

        // Category change is small (?50 diff, below ?500 threshold)
        CategoryAnalysis food = new CategoryAnalysis(
                TransactionCategory.FOOD, new BigDecimal("550"), new BigDecimal("55"),
                new BigDecimal("500"), new BigDecimal("50"), new BigDecimal("10.00"),
                TrendDirection.INCREASED, 1L);

        CategoryBreakdownResponse categories = new CategoryBreakdownResponse(
                new BigDecimal("1000"), List.of(food), TransactionCategory.FOOD);

        when(analyticsService.getSummary(from, to)).thenReturn(summary);
        when(analyticsService.getComparison(from, to)).thenReturn(comparison);
        when(analyticsService.getCategoryBreakdown(from, to)).thenReturn(categories);

        List<FinancialInsight> insights = insightService.generateInsights(from, to);

        assertThat(insights).noneMatch(i -> i.type() == InsightType.CATEGORY_INCREASE);
        assertThat(insights).noneMatch(i -> i.type() == InsightType.SAVINGS_IMPROVED);
    }
}
