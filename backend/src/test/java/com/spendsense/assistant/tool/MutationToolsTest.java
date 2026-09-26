package com.spendsense.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spendsense.assistant.action.PendingActionService;
import com.spendsense.assistant.dto.PendingActionDto;
import com.spendsense.assistant.tool.impl.mutation.CreateOrUpdateBudgetTool;
import com.spendsense.assistant.tool.impl.mutation.CreateOrUpdateGoalTool;
import com.spendsense.assistant.tool.impl.mutation.UpdateTransactionCategoryTool;
import com.spendsense.budget.BudgetService;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.goal.GoalService;
import com.spendsense.goal.dto.GoalResponse;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionService;
import com.spendsense.transaction.dto.TransactionResponse;
import com.spendsense.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MutationToolsTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private TransactionService transactionService;
    @Mock private BudgetService budgetService;
    @Mock private GoalService goalService;
    @Mock private PendingActionService pendingActionService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private SpendSenseUserPrincipal userPrincipal;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("ada@example.com", "hash", "Ada", "Asia/Kolkata");
        userPrincipal = new SpendSenseUserPrincipal(testUser);
    }

    @Test
    @DisplayName("Mutation Tool 1: update_transaction_category requires confirmation and enforces ownership")
    void updateTransactionCategory_requiresConfirmation() {
        UpdateTransactionCategoryTool tool = new UpdateTransactionCategoryTool(
                transactionRepository, transactionService, pendingActionService, objectMapper);

        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction(
                testUser,
                new BigDecimal("450.00"),
                "Swiggy",
                "Dinner delivery",
                TransactionCategory.SHOPPING,
                com.spendsense.transaction.TransactionType.EXPENSE,
                null,
                LocalDate.of(2026, 9, 20),
                null
        );

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        UUID pendingId = UUID.randomUUID();
        when(pendingActionService.createPendingAction(eq("update_transaction_category"), any(), any()))
                .thenReturn(new PendingActionDto(pendingId, "update_transaction_category", "Change category of Swiggy (₹450.00) from Shopping to Food & Dining", Instant.now().plusSeconds(900)));

        ToolExecutionResult<TransactionResponse> result = tool.execute(
                new UpdateTransactionCategoryTool.Input(txId, TransactionCategory.FOOD),
                userPrincipal
        );

        assertThat(result.requiresConfirmation()).isTrue();
        assertThat(result.pendingAction()).isInstanceOf(PendingActionDto.class);
        PendingActionDto dto = (PendingActionDto) result.pendingAction();
        assertThat(dto.actionId()).isEqualTo(pendingId);

        // Confirmed execution
        when(transactionService.correctCategory(txId, TransactionCategory.FOOD)).thenReturn(null);
        tool.executeConfirmedMutation(String.format("{\"transactionId\":\"%s\",\"category\":\"FOOD\"}", txId), userPrincipal);
        verify(transactionService).correctCategory(txId, TransactionCategory.FOOD);
    }

    @Test
    @DisplayName("Mutation Tool 1 IDOR: Attempting to update another user's transaction is rejected")
    void updateTransactionCategory_rejectsOtherUser() {
        UpdateTransactionCategoryTool tool = new UpdateTransactionCategoryTool(
                transactionRepository, transactionService, pendingActionService, objectMapper);

        User otherUser = new User("other@example.com", "hash", "Other", "Asia/Kolkata");
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction(
                otherUser,
                new BigDecimal("450.00"),
                "Swiggy",
                null,
                TransactionCategory.SHOPPING,
                com.spendsense.transaction.TransactionType.EXPENSE,
                null,
                LocalDate.of(2026, 9, 20),
                null
        );

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> tool.execute(
                new UpdateTransactionCategoryTool.Input(txId, TransactionCategory.FOOD),
                userPrincipal
        )).isInstanceOf(ApiException.class)
          .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);

        verify(pendingActionService, never()).createPendingAction(any(), any(), any());
    }

    @Test
    @DisplayName("Mutation Tool 2: create_or_update_budget requires confirmation and creates/updates budget")
    void createOrUpdateBudget_requiresConfirmation() {
        CreateOrUpdateBudgetTool tool = new CreateOrUpdateBudgetTool(
                budgetService, pendingActionService, objectMapper);

        when(budgetService.list(any(), any(), any())).thenReturn(List.of());

        UUID pendingId = UUID.randomUUID();
        when(pendingActionService.createPendingAction(eq("create_or_update_budget"), any(), any()))
                .thenReturn(new PendingActionDto(pendingId, "create_or_update_budget", "Set new monthly Food & Dining budget", Instant.now().plusSeconds(900)));

        ToolExecutionResult<BudgetResponse> result = tool.execute(
                new CreateOrUpdateBudgetTool.Input(TransactionCategory.FOOD, new BigDecimal("12000.00"), "2026-09"),
                userPrincipal
        );

        assertThat(result.requiresConfirmation()).isTrue();

        // Confirmed execution
        when(budgetService.create(any())).thenReturn(null);
        tool.executeConfirmedMutation("{\"category\":\"FOOD\",\"limitAmount\":12000.00,\"yearMonth\":\"2026-09\"}", userPrincipal);
        verify(budgetService).create(any());
    }

    @Test
    @DisplayName("Mutation Tool 3: create_or_update_goal rejects past dates and creates goal upon confirmation")
    void createOrUpdateGoal_validatesDateAndRequiresConfirmation() {
        CreateOrUpdateGoalTool tool = new CreateOrUpdateGoalTool(
                goalService, pendingActionService, objectMapper);

        // Past date rejected
        assertThatThrownBy(() -> tool.execute(
                new CreateOrUpdateGoalTool.Input("Car Down Payment", new BigDecimal("100000.00"), LocalDate.of(2020, 1, 1), BigDecimal.ZERO),
                userPrincipal
        )).isInstanceOf(ApiException.class)
          .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        // Future date accepted with confirmation
        UUID pendingId = UUID.randomUUID();
        when(pendingActionService.createPendingAction(eq("create_or_update_goal"), any(), any()))
                .thenReturn(new PendingActionDto(pendingId, "create_or_update_goal", "Create savings goal", Instant.now().plusSeconds(900)));

        ToolExecutionResult<GoalResponse> result = tool.execute(
                new CreateOrUpdateGoalTool.Input("Car Down Payment", new BigDecimal("100000.00"), LocalDate.of(2027, 1, 1), BigDecimal.ZERO),
                userPrincipal
        );

        assertThat(result.requiresConfirmation()).isTrue();

        // Confirmed execution
        when(goalService.create(any())).thenReturn(null);
        tool.executeConfirmedMutation("{\"name\":\"Car Down Payment\",\"targetAmount\":100000.00,\"targetDate\":\"2027-01-01\"}", userPrincipal);
        verify(goalService).create(any());
    }
}
