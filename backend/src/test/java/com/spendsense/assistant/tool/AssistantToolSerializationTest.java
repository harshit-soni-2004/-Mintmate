package com.spendsense.assistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spendsense.account.dto.FinancialAccountResponse;
import com.spendsense.analytics.TrendDirection;
import com.spendsense.analytics.dto.*;
import com.spendsense.assistant.AssistantOrchestrator;
import com.spendsense.assistant.dto.AssistantChatRequest;
import com.spendsense.assistant.dto.AssistantChatResponse;
import com.spendsense.assistant.model.provider.MockModelGateway;
import com.spendsense.assistant.tool.impl.*;
import com.spendsense.assistant.tool.impl.GetBudgetStatusTool.BudgetStatusResult;
import com.spendsense.assistant.tool.impl.SearchTransactionsTool.SearchResult;
import com.spendsense.assistant.tool.impl.SearchTransactionsTool.SanitizedTransactionItem;
import com.spendsense.budget.BudgetPeriod;
import com.spendsense.budget.BudgetStatus;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.budget.dto.BudgetSummaryResponse;
import com.spendsense.document.DocumentAccountType;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferDirection;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantToolSerializationTest {

    private ObjectMapper objectMapper;

    @Mock private CurrentUser currentUser;
    @Mock private GetSpendingSummaryTool spendingSummaryTool;
    @Mock private GetSpendingByCategoryTool spendingByCategoryTool;
    @Mock private SearchTransactionsTool searchTransactionsTool;
    @Mock private GetBudgetStatusTool budgetStatusTool;
    @Mock private GetFinancialAccountsTool financialAccountsTool;
    @Mock private CompareSpendingPeriodsTool compareSpendingPeriodsTool;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        User user = new User("ada@example.com", "hash", "Ada", "Asia/Kolkata");
        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
    }

    @Test
    @DisplayName("Tool 1: get_spending_summary envelope serializes to valid JSON with explicit dates")
    void testSpendingSummarySerialization() throws Exception {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        SpendingSummaryResponse summary = new SpendingSummaryResponse(
                BigDecimal.valueOf(75000.00),
                BigDecimal.valueOf(35000.00),
                BigDecimal.valueOf(40000.00),
                BigDecimal.valueOf(53.3),
                42,
                BigDecimal.valueOf(833.33),
                BigDecimal.valueOf(12500.00),
                start,
                end,
                BigDecimal.valueOf(15000.00),
                BigDecimal.valueOf(10000.00)
        );

        ToolResponseEnvelope<SpendingSummaryResponse> envelope = ToolResponseEnvelope.of(
                summary,
                start,
                end,
                "ALL_ACCOUNTS",
                "From 2026-08-01 to 2026-08-31, consumer expenses were ₹35,000.00.",
                Collections.emptyList()
        );

        String json = objectMapper.writeValueAsString(envelope);
        assertThat(json).doesNotContain("Failed to serialize");
        assertThat(json).contains("\"verified\":true");
        assertThat(json).contains("\"currency\":\"INR\"");

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.get("period").get("startDate").asText()).isEqualTo("2026-08-01");
        assertThat(root.get("period").get("endDate").asText()).isEqualTo("2026-08-31");
        assertThat(root.get("data").get("totalExpenses").asDouble()).isEqualTo(35000.00);
    }

    @Test
    @DisplayName("Tool 2: get_spending_by_category envelope serializes to valid JSON")
    void testCategoryBreakdownSerialization() throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 26);
        CategoryAnalysis cat = new CategoryAnalysis(
                TransactionCategory.FOOD,
                BigDecimal.valueOf(9617.50),
                BigDecimal.valueOf(42.5),
                BigDecimal.valueOf(9000.00),
                BigDecimal.valueOf(617.50),
                BigDecimal.valueOf(6.8),
                TrendDirection.INCREASED,
                18
        );
        CategoryBreakdownResponse breakdown = new CategoryBreakdownResponse(
                BigDecimal.valueOf(22600.00),
                List.of(cat),
                TransactionCategory.FOOD
        );

        ToolResponseEnvelope<CategoryBreakdownResponse> envelope = ToolResponseEnvelope.of(
                breakdown,
                start,
                end,
                "FOOD",
                "Spending on Food was ₹9,617.50.",
                Collections.emptyList()
        );

        String json = objectMapper.writeValueAsString(envelope);
        assertThat(json).contains("\"category\":\"FOOD\"");
        assertThat(json).contains("\"amount\":9617.5");

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.get("period").get("startDate").asText()).isEqualTo("2026-09-01");
        assertThat(root.get("period").get("endDate").asText()).isEqualTo("2026-09-26");
    }

    @Test
    @DisplayName("Tool 3: search_transactions envelope serializes to valid JSON")
    void testSearchTransactionsSerialization() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 15);
        SanitizedTransactionItem item = new SanitizedTransactionItem(
                UUID.randomUUID(),
                BigDecimal.valueOf(5400.00),
                date,
                TransactionCategory.SHOPPING,
                TransactionType.EXPENSE,
                TransferDirection.OUT,
                null,
                "<untrusted_merchant_text>Amazon India</untrusted_merchant_text>",
                null,
                "HDFC Salary",
                null
        );
        SearchResult result = new SearchResult(List.of(item), 0, 50, 1L, 1);

        ToolResponseEnvelope<SearchResult> envelope = ToolResponseEnvelope.of(
                result,
                date,
                date,
                "TRANSACTIONS",
                "Found 1 matching transaction.",
                Collections.emptyList()
        );

        String json = objectMapper.writeValueAsString(envelope);
        assertThat(json).contains("Amazon India");
        assertThat(json).contains("HDFC Salary");

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.get("data").get("transactions").get(0).get("date").asText()).isEqualTo("2026-09-15");
    }

    @Test
    @DisplayName("Tool 4: get_budget_status envelope serializes to valid JSON")
    void testBudgetStatusSerialization() throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        BudgetResponse b = new BudgetResponse(
                UUID.randomUUID(),
                TransactionCategory.FOOD,
                BigDecimal.valueOf(15000.00),
                BudgetPeriod.MONTHLY,
                start,
                end,
                BigDecimal.valueOf(9617.00),
                BigDecimal.valueOf(5383.00),
                64.1,
                14,
                BudgetStatus.ON_TRACK,
                Instant.now(),
                Instant.now()
        );
        BudgetSummaryResponse summary = new BudgetSummaryResponse(
                BigDecimal.valueOf(15000.00),
                BigDecimal.valueOf(9617.00),
                BigDecimal.valueOf(5383.00),
                64.1,
                1, 0, 0, 1,
                List.of(b)
        );
        BudgetStatusResult result = new BudgetStatusResult("2026-09", summary, List.of(b));

        ToolResponseEnvelope<BudgetStatusResult> envelope = ToolResponseEnvelope.of(
                result,
                start,
                end,
                "FOOD",
                "Food budget is ₹15,000.00 with ₹9,617.00 spent.",
                Collections.emptyList()
        );

        String json = objectMapper.writeValueAsString(envelope);
        assertThat(json).contains("\"yearMonth\":\"2026-09\"");
        assertThat(json).contains("\"status\":\"ON_TRACK\"");
    }

    @Test
    @DisplayName("Tool 5: get_financial_accounts envelope serializes to valid JSON with Instant timestamps")
    void testFinancialAccountsSerialization() throws Exception {
        FinancialAccountResponse account = new FinancialAccountResponse(
                UUID.randomUUID(),
                "Primary Checking",
                "HDFC Bank",
                DocumentAccountType.SAVINGS,
                "4321",
                "INR",
                true,
                true,
                Instant.now(),
                Instant.now()
        );

        ToolResponseEnvelope<List<FinancialAccountResponse>> envelope = ToolResponseEnvelope.of(
                List.of(account),
                "You have 1 linked account: HDFC Bank (...4321)."
        );

        String json = objectMapper.writeValueAsString(envelope);
        assertThat(json).contains("HDFC Bank");
        assertThat(json).contains("...4321");
    }

    @Test
    @DisplayName("Tool 6: compare_spending_periods envelope serializes to valid JSON")
    void testCompareSpendingPeriodsSerialization() throws Exception {
        LocalDate currentFrom = LocalDate.of(2026, 9, 1);
        LocalDate currentTo = LocalDate.of(2026, 9, 26);
        LocalDate prevFrom = LocalDate.of(2026, 8, 6);
        LocalDate prevTo = LocalDate.of(2026, 8, 31);

        TrendMetric expenseComp = new TrendMetric(
                BigDecimal.valueOf(25000.00),
                BigDecimal.valueOf(30000.00),
                BigDecimal.valueOf(-5000.00),
                BigDecimal.valueOf(-16.7),
                TrendDirection.DECREASED
        );
        TrendMetric incomeComp = new TrendMetric(
                BigDecimal.valueOf(75000.00),
                BigDecimal.valueOf(75000.00),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TrendDirection.UNCHANGED
        );
        TrendMetric netSavingsComp = new TrendMetric(
                BigDecimal.valueOf(50000.00),
                BigDecimal.valueOf(45000.00),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(11.1),
                TrendDirection.INCREASED
        );

        PeriodComparisonResponse comparison = new PeriodComparisonResponse(
                currentFrom, currentTo, prevFrom, prevTo,
                incomeComp, expenseComp, netSavingsComp,
                BigDecimal.valueOf(66.7), BigDecimal.valueOf(60.0), BigDecimal.valueOf(6.7)
        );

        ToolResponseEnvelope<PeriodComparisonResponse> envelope = ToolResponseEnvelope.of(
                comparison,
                currentFrom,
                currentTo,
                "COMPARISON",
                "Expenses decreased by 16.7% compared to prior period.",
                Collections.emptyList()
        );

        String json = objectMapper.writeValueAsString(envelope);
        assertThat(json).contains("\"currentFrom\":\"2026-09-01\"");
        assertThat(json).contains("\"previousFrom\":\"2026-08-06\"");
    }

    @Test
    @DisplayName("Integration Test: User asks 'How much did I spend last month?' -> executes, serializes, normal response, zero errors")
    void testFullOrchestrationSpendingSummaryFlow() {
        when(spendingSummaryTool.getName()).thenReturn(GetSpendingSummaryTool.NAME);
        doReturn(GetSpendingSummaryTool.Input.class).when(spendingSummaryTool).getInputType();

        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        SpendingSummaryResponse summary = new SpendingSummaryResponse(
                BigDecimal.valueOf(75000.00),
                BigDecimal.valueOf(35000.00),
                BigDecimal.valueOf(40000.00),
                BigDecimal.valueOf(53.3),
                42,
                BigDecimal.valueOf(833.33),
                BigDecimal.valueOf(12500.00),
                start,
                end,
                BigDecimal.valueOf(15000.00),
                BigDecimal.valueOf(10000.00)
        );

        when(spendingSummaryTool.execute(any(), any())).thenReturn(
                ToolExecutionResult.success(
                        ToolResponseEnvelope.of(
                                summary,
                                start,
                                end,
                                "ALL_ACCOUNTS",
                                "From 2026-08-01 to 2026-08-31, total verified income was ₹75,000.00, consumer expenses were ₹35,000.00, resulting in net savings of ₹40,000.00.",
                                Collections.emptyList()
                        )
                )
        );

        ToolRegistry registry = new ToolRegistry(List.of(spendingSummaryTool));
        ToolDispatcher dispatcher = new ToolDispatcher(registry, currentUser, objectMapper, Validation.buildDefaultValidatorFactory().getValidator());
        MockModelGateway gateway = new MockModelGateway(objectMapper);
        AssistantOrchestrator orchestrator = new AssistantOrchestrator(gateway, registry, dispatcher, objectMapper);

        AssistantChatRequest request = new AssistantChatRequest("How much did I spend last month?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_spending_summary");
        // Verify response contains verified summary numbers and absolutely no serialization error
        assertThat(response.answer()).doesNotContain("Failed to serialize tool result");
        assertThat(response.answer()).doesNotContain("{\"error\":");
        assertThat(response.answer()).contains("From 2026-08-01 to 2026-08-31");
        assertThat(response.answer()).contains("consumer expenses were ₹35,000.00");
    }

    @Test
    @DisplayName("Error Test: Tool failure returns structured safe error with ASST correlation ID")
    void testToolFailureReturnsSafeCorrelationId() {
        when(spendingSummaryTool.getName()).thenReturn(GetSpendingSummaryTool.NAME);
        doReturn(GetSpendingSummaryTool.Input.class).when(spendingSummaryTool).getInputType();
        when(spendingSummaryTool.execute(any(), any())).thenReturn(
                ToolExecutionResult.failure("Internal DB connection timeout")
        );

        ToolRegistry registry = new ToolRegistry(List.of(spendingSummaryTool));
        ToolDispatcher dispatcher = new ToolDispatcher(registry, currentUser, objectMapper, Validation.buildDefaultValidatorFactory().getValidator());
        MockModelGateway gateway = new MockModelGateway(objectMapper);
        AssistantOrchestrator orchestrator = new AssistantOrchestrator(gateway, registry, dispatcher, objectMapper);

        AssistantChatRequest request = new AssistantChatRequest("How much did I spend last month?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.answer()).contains("I couldn't retrieve your spending data right now");
        assertThat(response.answer()).contains("Reference: ASST-");
        // Must NOT leak internal database connection error
        assertThat(response.answer()).doesNotContain("Internal DB connection timeout");
    }
}
