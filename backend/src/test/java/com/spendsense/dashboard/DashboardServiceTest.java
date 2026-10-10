package com.spendsense.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.dashboard.dto.CategorySpend;
import com.spendsense.dashboard.dto.DailySpend;
import com.spendsense.dashboard.dto.DashboardDataResponse;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
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
class DashboardServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CurrentUser currentUser;

    private User user;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        user = new User("ada@example.com", "hash", "Ada", "Asia/Kolkata");
        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        service = new DashboardService(transactionRepository, currentUser);
    }

    @Test
    void aggregatesSummaryAndCategoryAndDailyDataCorrectly() {
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 30);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.INCOME), eq(fromDate), eq(toDate)))
                .thenReturn(new BigDecimal("100000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.EXPENSE), eq(fromDate), eq(toDate)))
                .thenReturn(new BigDecimal("40000.00"));

        LocalDate prevFrom = LocalDate.of(2026, 8, 1);
        LocalDate prevTo = LocalDate.of(2026, 8, 31);
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.INCOME), eq(prevFrom), eq(prevTo)))
                .thenReturn(new BigDecimal("80000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.EXPENSE), eq(prevFrom), eq(prevTo)))
                .thenReturn(new BigDecimal("50000.00"));

        when(transactionRepository.findCategorySpending(eq(user.getId()), eq(TransactionType.EXPENSE), eq(fromDate), eq(toDate)))
                .thenReturn(List.of(
                        new CategorySpend(TransactionCategory.FOOD, new BigDecimal("24000.00"), 5),
                        new CategorySpend(TransactionCategory.SHOPPING, new BigDecimal("16000.00"), 2)
                ));

        when(transactionRepository.findDailySpending(eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of(
                        new DailySpend(LocalDate.of(2026, 9, 8), TransactionType.EXPENSE, new BigDecimal("2450.00"), 3),
                        new DailySpend(LocalDate.of(2026, 9, 8), TransactionType.INCOME, new BigDecimal("5000.00"), 1)
                ));

        Transaction t = new Transaction(user, new BigDecimal("2450.00"), "Store", TransactionCategory.FOOD,
                TransactionType.EXPENSE, LocalDate.of(2026, 9, 8), null);
        when(transactionRepository.findTop10ByUser_IdOrderByTransactionDateDescCreatedAtDesc(eq(user.getId())))
                .thenReturn(List.of(t));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of(t));

        DashboardDataResponse result = service.getDashboardData("2026-09", null, null);

        assertThat(result.summary().totalIncome()).isEqualByComparingTo("100000.00");
        assertThat(result.summary().totalExpenses()).isEqualByComparingTo("40000.00");
        assertThat(result.summary().netSavings()).isEqualByComparingTo("60000.00");
        assertThat(result.summary().savingsRate()).isEqualByComparingTo("60.0");
        assertThat(result.summary().expenseChangePercentage()).isEqualByComparingTo("-20.0");
        assertThat(result.summary().incomeChangePercentage()).isEqualByComparingTo("25.0");

        assertThat(result.categorySpending()).hasSize(2);
        assertThat(result.categorySpending().get(0).category()).isEqualTo(TransactionCategory.FOOD);
        assertThat(result.categorySpending().get(0).amount()).isEqualByComparingTo("24000.00");
        assertThat(result.categorySpending().get(0).percentage()).isEqualByComparingTo("60.0");

        assertThat(result.dailySpending()).hasSize(1);
        assertThat(result.dailySpending().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(result.dailySpending().get(0).totalExpense()).isEqualByComparingTo("2450.00");
        assertThat(result.dailySpending().get(0).totalIncome()).isEqualByComparingTo("5000.00");
        assertThat(result.dailySpending().get(0).transactionCount()).isEqualTo(4);

        assertThat(result.recentTransactions()).hasSize(1);
        assertThat(result.periodTransactions()).hasSize(1);

        verify(transactionRepository).findCategorySpending(eq(user.getId()), eq(TransactionType.EXPENSE), eq(fromDate), eq(toDate));
    }

    @Test
    void handlesEmptyMonthWithZeroTransactions() {
        LocalDate fromDate = LocalDate.of(2026, 11, 1);
        LocalDate toDate = LocalDate.of(2026, 11, 30);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.INCOME), eq(fromDate), eq(toDate)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.EXPENSE), eq(fromDate), eq(toDate)))
                .thenReturn(BigDecimal.ZERO);

        LocalDate prevFrom = LocalDate.of(2026, 10, 1);
        LocalDate prevTo = LocalDate.of(2026, 10, 31);
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.INCOME), eq(prevFrom), eq(prevTo)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.EXPENSE), eq(prevFrom), eq(prevTo)))
                .thenReturn(BigDecimal.ZERO);

        when(transactionRepository.findCategorySpending(eq(user.getId()), eq(TransactionType.EXPENSE), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());
        when(transactionRepository.findDailySpending(eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());
        when(transactionRepository.findTop10ByUser_IdOrderByTransactionDateDescCreatedAtDesc(eq(user.getId())))
                .thenReturn(List.of());
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());

        DashboardDataResponse result = service.getDashboardData("2026-11", null, null);

        assertThat(result.summary().totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.summary().totalExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.summary().netSavings()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.summary().savingsRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.dailySpending()).isEmpty();
        assertThat(result.periodTransactions()).isEmpty();
        assertThat(result.fromDate()).isEqualTo(fromDate);
        assertThat(result.toDate()).isEqualTo(toDate);
    }

    @Test
    void handlesFebruaryNonLeapYear28Days() {
        LocalDate fromDate = LocalDate.of(2026, 2, 1);
        LocalDate toDate = LocalDate.of(2026, 2, 28);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findCategorySpending(eq(user.getId()), eq(TransactionType.EXPENSE), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());
        when(transactionRepository.findDailySpending(eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());
        when(transactionRepository.findTop10ByUser_IdOrderByTransactionDateDescCreatedAtDesc(eq(user.getId())))
                .thenReturn(List.of());
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());

        DashboardDataResponse result = service.getDashboardData("2026-02", null, null);

        assertThat(result.fromDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(result.toDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        verify(transactionRepository).findDailySpending(eq(user.getId()), eq(fromDate), eq(toDate));
    }

    @Test
    void handlesFebruaryLeapYear29Days() {
        LocalDate fromDate = LocalDate.of(2024, 2, 1);
        LocalDate toDate = LocalDate.of(2024, 2, 29);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findCategorySpending(eq(user.getId()), eq(TransactionType.EXPENSE), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());
        when(transactionRepository.findDailySpending(eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());
        when(transactionRepository.findTop10ByUser_IdOrderByTransactionDateDescCreatedAtDesc(eq(user.getId())))
                .thenReturn(List.of());
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());

        DashboardDataResponse result = service.getDashboardData("2024-02", null, null);

        assertThat(result.fromDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(result.toDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        verify(transactionRepository).findDailySpending(eq(user.getId()), eq(fromDate), eq(toDate));
    }

    @Test
    void handles31DayMonthAugustAndReconcilesDailyTransactions() {
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 31);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.INCOME), eq(fromDate), eq(toDate)))
                .thenReturn(new BigDecimal("60000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), eq(TransactionType.EXPENSE), eq(fromDate), eq(toDate)))
                .thenReturn(new BigDecimal("35000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                eq(user.getId()), any(), eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 31))))
                .thenReturn(new BigDecimal("20000.00"));

        when(transactionRepository.findCategorySpending(eq(user.getId()), eq(TransactionType.EXPENSE), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());
        when(transactionRepository.findDailySpending(eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of(
                        new DailySpend(LocalDate.of(2026, 8, 1), TransactionType.INCOME, new BigDecimal("60000.00"), 1),
                        new DailySpend(LocalDate.of(2026, 8, 5), TransactionType.EXPENSE, new BigDecimal("15000.00"), 2),
                        new DailySpend(LocalDate.of(2026, 8, 20), TransactionType.EXPENSE, new BigDecimal("20000.00"), 3)
                ));
        when(transactionRepository.findTop10ByUser_IdOrderByTransactionDateDescCreatedAtDesc(eq(user.getId())))
                .thenReturn(List.of());
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                eq(user.getId()), eq(fromDate), eq(toDate)))
                .thenReturn(List.of());

        DashboardDataResponse result = service.getDashboardData("2026-08", null, null);

        assertThat(result.fromDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.toDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(result.dailySpending()).hasSize(3);

        BigDecimal sumDailyExpense = result.dailySpending().stream()
                .map(d -> d.totalExpense())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumDailyIncome = result.dailySpending().stream()
                .map(d -> d.totalIncome())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sumDailyExpense).isEqualByComparingTo(result.summary().totalExpenses());
        assertThat(sumDailyIncome).isEqualByComparingTo(result.summary().totalIncome());
    }
}
