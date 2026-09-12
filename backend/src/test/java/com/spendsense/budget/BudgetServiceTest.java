package com.spendsense.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.budget.dto.BudgetRequest;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.budget.dto.BudgetSummaryResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;

    private User user;
    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        user = new User("priya@example.com", "hash", "Priya Sharma", "Asia/Kolkata");
        lenient().when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        budgetService = new BudgetService(budgetRepository, transactionRepository, userRepository, currentUser);
    }

    @Test
    void createsBudgetSuccessfully() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(budgetRepository.existsByUser_IdAndCategoryAndPeriodAndStartDate(any(), any(), any(), any())).thenReturn(false);
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.countByUserIdAndCategoryAndTypeAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(0L);

        BudgetRequest req = new BudgetRequest(
                new BigDecimal("8000.00"),
                TransactionCategory.FOOD,
                BudgetPeriod.MONTHLY,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );

        BudgetResponse res = budgetService.create(req);

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(res.limitAmount()).isEqualByComparingTo("8000.00");
        assertThat(res.category()).isEqualTo(TransactionCategory.FOOD);
        assertThat(res.status()).isEqualTo(BudgetStatus.ON_TRACK);
    }

    @Test
    void rejectsDuplicateBudgetForSamePeriodAndCategory() {
        when(budgetRepository.existsByUser_IdAndCategoryAndPeriodAndStartDate(any(), any(), any(), any())).thenReturn(true);

        BudgetRequest req = new BudgetRequest(
                new BigDecimal("8000.00"),
                TransactionCategory.FOOD,
                BudgetPeriod.MONTHLY,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );

        assertThatThrownBy(() -> budgetService.create(req))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("BUDGET_ALREADY_EXISTS");
    }

    @Test
    void rejectsInvalidDateRange() {
        BudgetRequest req = new BudgetRequest(
                new BigDecimal("8000.00"),
                TransactionCategory.FOOD,
                BudgetPeriod.MONTHLY,
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 9, 1)
        );

        assertThatThrownBy(() -> budgetService.create(req))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void calculatesOnTrackStatusUnder75Percent() {
        Budget budget = new Budget(user, TransactionCategory.FOOD, new BigDecimal("10000.00"),
                BudgetPeriod.MONTHLY, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        when(transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
                user.getId(), TransactionCategory.FOOD, TransactionType.EXPENSE, budget.getStartDate(), budget.getEndDate()))
                .thenReturn(new BigDecimal("4500.00"));
        when(transactionRepository.countByUserIdAndCategoryAndTypeAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(3L);

        BudgetResponse res = budgetService.toResponse(budget);

        assertThat(res.spentAmount()).isEqualByComparingTo("4500.00");
        assertThat(res.remainingAmount()).isEqualByComparingTo("5500.00");
        assertThat(res.percentageUsed()).isEqualTo(45.0);
        assertThat(res.status()).isEqualTo(BudgetStatus.ON_TRACK);
    }

    @Test
    void calculatesWarningStatusBetween75And100Percent() {
        Budget budget = new Budget(user, TransactionCategory.FOOD, new BigDecimal("10000.00"),
                BudgetPeriod.MONTHLY, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        when(transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
                user.getId(), TransactionCategory.FOOD, TransactionType.EXPENSE, budget.getStartDate(), budget.getEndDate()))
                .thenReturn(new BigDecimal("8500.00"));
        when(transactionRepository.countByUserIdAndCategoryAndTypeAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(5L);

        BudgetResponse res = budgetService.toResponse(budget);

        assertThat(res.spentAmount()).isEqualByComparingTo("8500.00");
        assertThat(res.remainingAmount()).isEqualByComparingTo("1500.00");
        assertThat(res.percentageUsed()).isEqualTo(85.0);
        assertThat(res.status()).isEqualTo(BudgetStatus.WARNING);
    }

    @Test
    void calculatesExceededStatusAtOrOver100Percent() {
        Budget budget = new Budget(user, TransactionCategory.FOOD, new BigDecimal("10000.00"),
                BudgetPeriod.MONTHLY, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        when(transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
                user.getId(), TransactionCategory.FOOD, TransactionType.EXPENSE, budget.getStartDate(), budget.getEndDate()))
                .thenReturn(new BigDecimal("10500.00"));
        when(transactionRepository.countByUserIdAndCategoryAndTypeAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(6L);

        BudgetResponse res = budgetService.toResponse(budget);

        assertThat(res.spentAmount()).isEqualByComparingTo("10500.00");
        assertThat(res.remainingAmount()).isEqualByComparingTo("-500.00");
        assertThat(res.percentageUsed()).isEqualTo(105.0);
        assertThat(res.status()).isEqualTo(BudgetStatus.EXCEEDED);
    }

    @Test
    void calculatesBudgetSummaryCorrectly() {
        Budget b1 = new Budget(user, TransactionCategory.FOOD, new BigDecimal("8000.00"),
                BudgetPeriod.MONTHLY, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        Budget b2 = new Budget(user, TransactionCategory.SHOPPING, new BigDecimal("5000.00"),
                BudgetPeriod.MONTHLY, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        when(budgetRepository.findActiveBudgets(any(), any(), any())).thenReturn(List.of(b1, b2));
        when(transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
                user.getId(), TransactionCategory.FOOD, TransactionType.EXPENSE, b1.getStartDate(), b1.getEndDate()))
                .thenReturn(new BigDecimal("6200.00"));
        when(transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
                user.getId(), TransactionCategory.SHOPPING, TransactionType.EXPENSE, b2.getStartDate(), b2.getEndDate()))
                .thenReturn(new BigDecimal("3100.00"));

        BudgetSummaryResponse summary = budgetService.getSummary("2026-09", null, null);

        assertThat(summary.totalBudget()).isEqualByComparingTo("13000.00");
        assertThat(summary.totalSpent()).isEqualByComparingTo("9300.00");
        assertThat(summary.totalRemaining()).isEqualByComparingTo("3700.00");
        assertThat(summary.percentageUsed()).isEqualTo(71.54);
        assertThat(summary.totalBudgets()).isEqualTo(2);
        assertThat(summary.budgets()).hasSize(2);
    }

    @Test
    void userCannotAccessAnotherUsersBudget() {
        when(budgetRepository.findByIdAndUser_Id(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.get(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("BUDGET_NOT_FOUND");
    }
}
