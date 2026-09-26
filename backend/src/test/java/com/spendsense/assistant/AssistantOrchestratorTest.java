package com.spendsense.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spendsense.assistant.dto.AssistantChatRequest;
import com.spendsense.assistant.dto.AssistantChatResponse;
import com.spendsense.assistant.dto.ChatMessageDto;
import com.spendsense.assistant.model.provider.MockModelGateway;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolDispatcher;
import com.spendsense.assistant.tool.ToolRegistry;
import com.spendsense.assistant.tool.impl.*;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.user.User;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantOrchestratorTest {

    @Mock private CurrentUser currentUser;
    @Mock private GetSpendingSummaryTool spendingSummaryTool;
    @Mock private GetSpendingByCategoryTool spendingByCategoryTool;
    @Mock private SearchTransactionsTool searchTransactionsTool;
    @Mock private GetBudgetStatusTool budgetStatusTool;
    @Mock private GetFinancialAccountsTool financialAccountsTool;
    @Mock private CompareSpendingPeriodsTool compareSpendingPeriodsTool;

    private AssistantOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(spendingSummaryTool.getName()).thenReturn(GetSpendingSummaryTool.NAME);
        when(spendingByCategoryTool.getName()).thenReturn(GetSpendingByCategoryTool.NAME);
        when(searchTransactionsTool.getName()).thenReturn(SearchTransactionsTool.NAME);
        when(budgetStatusTool.getName()).thenReturn(GetBudgetStatusTool.NAME);
        when(financialAccountsTool.getName()).thenReturn(GetFinancialAccountsTool.NAME);
        when(compareSpendingPeriodsTool.getName()).thenReturn(CompareSpendingPeriodsTool.NAME);

        doReturn(GetSpendingSummaryTool.Input.class).when(spendingSummaryTool).getInputType();
        doReturn(GetSpendingByCategoryTool.Input.class).when(spendingByCategoryTool).getInputType();
        doReturn(SearchTransactionsTool.Input.class).when(searchTransactionsTool).getInputType();
        doReturn(GetBudgetStatusTool.Input.class).when(budgetStatusTool).getInputType();
        doReturn(GetFinancialAccountsTool.Input.class).when(financialAccountsTool).getInputType();
        doReturn(CompareSpendingPeriodsTool.Input.class).when(compareSpendingPeriodsTool).getInputType();

        List<FinancialTool<?, ?>> tools = List.of(
                spendingSummaryTool,
                spendingByCategoryTool,
                searchTransactionsTool,
                budgetStatusTool,
                financialAccountsTool,
                compareSpendingPeriodsTool
        );

        ToolRegistry registry = new ToolRegistry(tools);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ToolDispatcher dispatcher = new ToolDispatcher(registry, currentUser, objectMapper, Validation.buildDefaultValidatorFactory().getValidator());

        MockModelGateway modelGateway = new MockModelGateway();
        orchestrator = new AssistantOrchestrator(modelGateway, registry, dispatcher);

        User user = new User("ada@example.com", "hash", "Ada", "Asia/Kolkata");
        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
    }

    @Test
    @DisplayName("Case 1: 'How much did I spend last month?' dispatches get_spending_summary")
    void shouldHandleSpendingSummaryQuery() {
        when(spendingSummaryTool.execute(any(), any())).thenReturn(
                com.spendsense.assistant.tool.ToolExecutionResult.success(
                        com.spendsense.assistant.tool.ToolResponseEnvelope.of(null, "Last month your consumer spending was ₹35,000.")
                )
        );

        AssistantChatRequest request = new AssistantChatRequest("How much did I spend last month?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_spending_summary");
        assertThat(response.answer()).contains("Last month your consumer spending was ₹35,000.");
        verify(spendingSummaryTool).execute(any(), any());
    }

    @Test
    @DisplayName("Case 2: 'How much did I spend on food?' dispatches get_spending_by_category")
    void shouldHandleCategoryQuery() {
        when(spendingByCategoryTool.execute(any(), any())).thenReturn(
                com.spendsense.assistant.tool.ToolExecutionResult.success(
                        com.spendsense.assistant.tool.ToolResponseEnvelope.of(null, "Spending on Food & Dining was ₹9,617.")
                )
        );

        AssistantChatRequest request = new AssistantChatRequest("How much did I spend on food?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_spending_by_category");
        assertThat(response.answer()).contains("Spending on Food & Dining was ₹9,617.");
        verify(spendingByCategoryTool).execute(any(), any());
    }

    @Test
    @DisplayName("Case 3: 'Did I spend more this month than last month?' dispatches compare_spending_periods")
    void shouldHandleComparisonQuery() {
        when(compareSpendingPeriodsTool.execute(any(), any())).thenReturn(
                com.spendsense.assistant.tool.ToolExecutionResult.success(
                        com.spendsense.assistant.tool.ToolResponseEnvelope.of(null, "Expenses changed by -12.5% compared to last month.")
                )
        );

        AssistantChatRequest request = new AssistantChatRequest("Did I spend more this month than last month?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("compare_spending_periods");
        assertThat(response.answer()).contains("Expenses changed by -12.5%");
        verify(compareSpendingPeriodsTool).execute(any(), any());
    }

    @Test
    @DisplayName("Case 4: 'What transactions over ₹5,000 do I have?' dispatches search_transactions")
    void shouldHandleTransactionSearchQuery() {
        when(searchTransactionsTool.execute(any(), any())).thenReturn(
                com.spendsense.assistant.tool.ToolExecutionResult.success(
                        com.spendsense.assistant.tool.ToolResponseEnvelope.of(null, "Found 3 transactions over ₹5,000.")
                )
        );

        AssistantChatRequest request = new AssistantChatRequest("What transactions over 5000 do I have?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("search_transactions");
        assertThat(response.answer()).contains("Found 3 transactions over ₹5,000.");
        verify(searchTransactionsTool).execute(any(), any());
    }

    @Test
    @DisplayName("Case 5: 'Am I within my food budget?' dispatches get_budget_status")
    void shouldHandleBudgetStatusQuery() {
        when(budgetStatusTool.execute(any(), any())).thenReturn(
                com.spendsense.assistant.tool.ToolExecutionResult.success(
                        com.spendsense.assistant.tool.ToolResponseEnvelope.of(null, "Food budget is ₹10,000 with ₹6,000 spent (60%). Status: ON_TRACK.")
                )
        );

        AssistantChatRequest request = new AssistantChatRequest("Am I within my food budget?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_budget_status");
        assertThat(response.answer()).contains("Status: ON_TRACK");
        verify(budgetStatusTool).execute(any(), any());
    }

    @Test
    @DisplayName("Case 6: 'What bank accounts do I have linked?' dispatches get_financial_accounts")
    void shouldHandleAccountsQuery() {
        when(financialAccountsTool.execute(any(), any())).thenReturn(
                com.spendsense.assistant.tool.ToolExecutionResult.success(
                        com.spendsense.assistant.tool.ToolResponseEnvelope.of(null, "You have 2 linked accounts: Kotak Mahindra (...4821), SBI (...1024).")
                )
        );

        AssistantChatRequest request = new AssistantChatRequest("What bank accounts do I have linked?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_financial_accounts");
        assertThat(response.answer()).contains("Kotak Mahindra (...4821)");
        verify(financialAccountsTool).execute(any(), any());
    }
}
