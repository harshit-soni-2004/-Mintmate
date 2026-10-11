package com.spendsense.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.analytics.dto.SpendingTrendResponse;
import com.spendsense.dashboard.dto.CategorySpend;
import com.spendsense.dashboard.dto.DailySpend;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
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
class AnalyticsServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private CurrentUser currentUser;

    private User user;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        user = new User("rahul@example.com", "hash", "Rahul Sen", "Asia/Kolkata");
        lenient().when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        analyticsService = new AnalyticsService(transactionRepository, currentUser);
    }

    @Test
    void calculatesSummaryCorrectly() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, from, to))
                .thenReturn(new BigDecimal("50000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("30000.00"));
        when(transactionRepository.countByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(15L);
        when(transactionRepository.countByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, from, to))
                .thenReturn(2L);
        when(transactionRepository.maxAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("8000.00"));

        SpendingSummaryResponse summary = analyticsService.getSummary(from, to);

        assertThat(summary.totalIncome()).isEqualByComparingTo("50000.00");
        assertThat(summary.totalExpenses()).isEqualByComparingTo("30000.00");
        assertThat(summary.netSavings()).isEqualByComparingTo("20000.00");
        assertThat(summary.savingsRate()).isEqualByComparingTo("40.00");
        assertThat(summary.transactionCount()).isEqualTo(17L);
        assertThat(summary.averageExpense()).isEqualByComparingTo("2000.00");
        assertThat(summary.largestExpense()).isEqualByComparingTo("8000.00");
    }

    @Test
    void calculatesPeriodComparisonWithIncreaseAndDecrease() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        LocalDate prevFrom = LocalDate.of(2026, 8, 1);
        LocalDate prevTo = LocalDate.of(2026, 8, 31);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, from, to))
                .thenReturn(new BigDecimal("60000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("32000.00"));

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, prevFrom, prevTo))
                .thenReturn(new BigDecimal("50000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, prevFrom, prevTo))
                .thenReturn(new BigDecimal("25000.00"));

        PeriodComparisonResponse comparison = analyticsService.getComparison(from, to);

        assertThat(comparison.expenses().currentAmount()).isEqualByComparingTo("32000.00");
        assertThat(comparison.expenses().previousAmount()).isEqualByComparingTo("25000.00");
        assertThat(comparison.expenses().changeAmount()).isEqualByComparingTo("7000.00");
        assertThat(comparison.expenses().percentageChange()).isEqualByComparingTo("28.00");
        assertThat(comparison.expenses().direction()).isEqualTo(TrendDirection.INCREASED);

        assertThat(comparison.currentSavingsRate()).isEqualByComparingTo("46.67");
        assertThat(comparison.previousSavingsRate()).isEqualByComparingTo("50.00");
        assertThat(comparison.savingsRateChange()).isEqualByComparingTo("-3.33");
    }

    @Test
    void calculatesCategoryBreakdownAndIdentifiesTopCategory() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        LocalDate prevFrom = LocalDate.of(2026, 8, 1);
        LocalDate prevTo = LocalDate.of(2026, 8, 31);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("12500.00"));

        when(transactionRepository.findCategorySpending(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(List.of(
                        new CategorySpend(TransactionCategory.FOOD, new BigDecimal("8000.00"), 10L),
                        new CategorySpend(TransactionCategory.TRAVEL, new BigDecimal("2000.00"), 4L),
                        new CategorySpend(TransactionCategory.SHOPPING, new BigDecimal("2500.00"), 2L)
                ));

        when(transactionRepository.findCategorySpending(user.getId(), TransactionType.EXPENSE, prevFrom, prevTo))
                .thenReturn(List.of(
                        new CategorySpend(TransactionCategory.FOOD, new BigDecimal("5000.00"), 8L),
                        new CategorySpend(TransactionCategory.TRAVEL, new BigDecimal("3000.00"), 6L),
                        new CategorySpend(TransactionCategory.BILLS, new BigDecimal("1500.00"), 1L)
                ));

        CategoryBreakdownResponse res = analyticsService.getCategoryBreakdown(from, to);

        assertThat(res.totalExpenses()).isEqualByComparingTo("12500.00");
        assertThat(res.topCategory()).isEqualTo(TransactionCategory.FOOD);
        assertThat(res.categories()).hasSize(4); // FOOD, TRAVEL, SHOPPING + BILLS (previous period)

        var food = res.categories().stream().filter(c -> c.category() == TransactionCategory.FOOD).findFirst().orElseThrow();
        assertThat(food.amount()).isEqualByComparingTo("8000.00");
        assertThat(food.previousAmount()).isEqualByComparingTo("5000.00");
        assertThat(food.changeAmount()).isEqualByComparingTo("3000.00");
        assertThat(food.percentageChange()).isEqualByComparingTo("60.00");
        assertThat(food.direction()).isEqualTo(TrendDirection.INCREASED);

        var travel = res.categories().stream().filter(c -> c.category() == TransactionCategory.TRAVEL).findFirst().orElseThrow();
        assertThat(travel.amount()).isEqualByComparingTo("2000.00");
        assertThat(travel.previousAmount()).isEqualByComparingTo("3000.00");
        assertThat(travel.changeAmount()).isEqualByComparingTo("-1000.00");
        assertThat(travel.direction()).isEqualTo(TrendDirection.DECREASED);
    }

    @Test
    void generatesDailyTrendsWithZeroFilledDates() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 5); // 5 days

        when(transactionRepository.findDailySpending(user.getId(), from, to))
                .thenReturn(List.of(
                        new DailySpend(LocalDate.of(2026, 9, 2), TransactionType.EXPENSE, new BigDecimal("1500.00"), 2L),
                        new DailySpend(LocalDate.of(2026, 9, 4), TransactionType.EXPENSE, new BigDecimal("800.00"), 1L)
                ));

        SpendingTrendResponse trend = analyticsService.getTrends(AggregationPeriod.DAILY, from, to);

        assertThat(trend.points()).hasSize(5); // all 5 days must be present
        assertThat(trend.points().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(trend.points().get(0).totalExpense()).isEqualByComparingTo("0.00");

        assertThat(trend.points().get(1).date()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(trend.points().get(1).totalExpense()).isEqualByComparingTo("1500.00");

        assertThat(trend.points().get(2).date()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(trend.points().get(2).totalExpense()).isEqualByComparingTo("0.00");

        assertThat(trend.points().get(3).date()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(trend.points().get(3).totalExpense()).isEqualByComparingTo("800.00");
    }

    @Test
    void returnsNullPercentageChangeWhenPreviousPeriodHasZeroOrNoSpending() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        LocalDate prevFrom = LocalDate.of(2026, 8, 1);
        LocalDate prevTo = LocalDate.of(2026, 8, 31);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, from, to))
                .thenReturn(new BigDecimal("10000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("1406.00"));

        // Prior period has 0 income and 0 expenses (e.g. brand new user)
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, prevFrom, prevTo))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, prevFrom, prevTo))
                .thenReturn(BigDecimal.ZERO);

        PeriodComparisonResponse comparison = analyticsService.getComparison(from, to);

        assertThat(comparison.expenses().currentAmount()).isEqualByComparingTo("1406.00");
        assertThat(comparison.expenses().previousAmount()).isEqualByComparingTo("0.00");
        assertThat(comparison.expenses().changeAmount()).isEqualByComparingTo("1406.00");
        // Must suppress the misleading +100% calculation
        assertThat(comparison.expenses().percentageChange()).isNull();
        assertThat(comparison.expenses().direction()).isEqualTo(TrendDirection.UNCHANGED);
        assertThat(comparison.hasPriorPeriodData()).isFalse();
    }
}
