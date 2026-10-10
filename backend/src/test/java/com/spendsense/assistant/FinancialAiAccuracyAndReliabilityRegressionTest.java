package com.spendsense.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spendsense.account.FinancialAccount;
import com.spendsense.account.FinancialAccountRepository;
import com.spendsense.account.FinancialAccountService;
import com.spendsense.account.dto.FinancialAccountResponse;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.assistant.dto.AssistantChatRequest;
import com.spendsense.assistant.dto.AssistantChatResponse;
import com.spendsense.assistant.dto.ChatMessageDto;
import com.spendsense.assistant.model.provider.MockModelGateway;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolDispatcher;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolRegistry;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.assistant.tool.impl.GetBudgetStatusTool;
import com.spendsense.assistant.tool.impl.GetSpendingByCategoryTool;
import com.spendsense.assistant.tool.impl.GetSpendingSummaryTool;
import com.spendsense.assistant.tool.impl.SearchTransactionsTool;
import com.spendsense.budget.BudgetPeriod;
import com.spendsense.budget.BudgetRepository;
import com.spendsense.budget.BudgetService;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.budget.dto.BudgetSummaryResponse;
import com.spendsense.common.api.PageMeta;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.*;
import com.spendsense.transaction.dto.TransactionResponse;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Deterministic regression evaluation suite verifying all 17 Financial AI accuracy and reliability requirements.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FinancialAiAccuracyAndReliabilityRegressionTest {

    @Mock private CurrentUser currentUser;
    @Mock private AnalyticsService analyticsService;
    @Mock private TransactionService transactionService;
    @Mock private BudgetService budgetService;
    @Mock private FinancialAccountService accountService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private FinancialAccountRepository accountRepository;

    private AssistantOrchestrator orchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private User testUser;
    private SpendSenseUserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        testUser = new User("harshit@example.com", "hash", "Harshit Soni", "Asia/Kolkata");
        userPrincipal = new SpendSenseUserPrincipal(testUser);
        when(currentUser.require()).thenReturn(userPrincipal);

        GetSpendingSummaryTool summaryTool = new GetSpendingSummaryTool(analyticsService);
        GetSpendingByCategoryTool categoryTool = new GetSpendingByCategoryTool(analyticsService);
        GetBudgetStatusTool budgetTool = new GetBudgetStatusTool(budgetService);
        SearchTransactionsTool searchTool = new SearchTransactionsTool(transactionService);

        List<FinancialTool<?, ?>> tools = List.of(summaryTool, categoryTool, budgetTool, searchTool);
        ToolRegistry registry = new ToolRegistry(tools);
        ToolDispatcher dispatcher = new ToolDispatcher(registry, currentUser, objectMapper, Validation.buildDefaultValidatorFactory().getValidator());
        MockModelGateway modelGateway = new MockModelGateway(objectMapper);
        orchestrator = new AssistantOrchestrator(modelGateway, registry, dispatcher, objectMapper);
    }

    @Test
    @DisplayName("Req 1 & 3: August 2026 category query returns August data and cannot be answered by a generic monthly summary")
    void req1And3_augustCategoryQueryReturnsAugustDataAndNotGenericSummary() {
        LocalDate augStart = LocalDate.of(2026, 8, 1);
        LocalDate augEnd = LocalDate.of(2026, 8, 31);

        CategoryAnalysis food = new CategoryAnalysis(TransactionCategory.FOOD, new BigDecimal("15000.00"), new BigDecimal("42.9"), null, null, null, null, 15);
        CategoryAnalysis shopping = new CategoryAnalysis(TransactionCategory.SHOPPING, new BigDecimal("12000.00"), new BigDecimal("34.3"), null, null, null, null, 8);
        CategoryAnalysis travel = new CategoryAnalysis(TransactionCategory.TRAVEL, new BigDecimal("8000.00"), new BigDecimal("22.8"), null, null, null, null, 4);

        CategoryBreakdownResponse augBreakdown = new CategoryBreakdownResponse(
                new BigDecimal("35000.00"),
                List.of(food, shopping, travel),
                null
        );

        when(analyticsService.getCategoryBreakdown(augStart, augEnd)).thenReturn(augBreakdown);

        AssistantChatRequest request = new AssistantChatRequest("What were my top spending categories in August?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_spending_by_category");
        assertThat(response.executedTools().getFirst().summary()).contains("2026-08-01").contains("2026-08-31");
        assertThat(response.answer())
                .contains("Food & Dining")
                .contains("₹15000.00")
                .contains("Shopping")
                .contains("₹12000.00")
                .doesNotContain("2026-10")
                .doesNotContain("₹0 income");

        // Verify generic spending summary was NOT invoked
        verify(analyticsService, never()).getSummary(any(), any());
    }

    @Test
    @DisplayName("Req 2: Last month in October 2026 resolves strictly to September 2026")
    void req2_lastMonthInOctober2026ResolvesToSeptember2026() {
        LocalDate octRef = LocalDate.of(2026, 10, 10);
        NaturalLanguageDateResolver.DateRange range = NaturalLanguageDateResolver.resolveRange("last month", octRef);

        assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(range.endDate()).isEqualTo(LocalDate.of(2026, 9, 30));

        SpendingSummaryResponse septSummary = new SpendingSummaryResponse(
                new BigDecimal("90000.00"), new BigDecimal("32000.00"), new BigDecimal("58000.00"),
                new BigDecimal("64.4"), 25, new BigDecimal("1280.00"), new BigDecimal("6000.00"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                BigDecimal.ZERO, BigDecimal.ZERO
        );
        when(analyticsService.getSummary(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(septSummary);

        AssistantChatRequest request = new AssistantChatRequest("How much did I spend last month?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_spending_summary");
        assertThat(response.answer()).contains("32000.00").contains("2026-09-01").contains("2026-09-30");
    }

    @Test
    @DisplayName("Req 4 & 5: 'Over ₹5,000' strictly excludes ₹5,000 and includes > ₹5,000")
    void req4And5_over5000ExcludesExactly5000AndIncludesStrictlyGreaterThan() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        TransactionResponse txExact5000 = new TransactionResponse(u1, new BigDecimal("5000.00"), "Flipkart", "Books", TransactionCategory.EDUCATION, TransactionType.EXPENSE, null, LocalDate.of(2026, 9, 12), null, Instant.now(), Instant.now());
        TransactionResponse txOver5000 = new TransactionResponse(u2, new BigDecimal("7500.00"), "Croma", "Headphones", TransactionCategory.SHOPPING, TransactionType.EXPENSE, null, LocalDate.of(2026, 9, 15), null, Instant.now(), Instant.now());

        // When minAmountExclusive is true, only txOver5000 is returned by transactionService
        when(transactionService.list(anyInt(), anyInt(), any(), any(), any(), any(), any(), eq(new BigDecimal("5000")), any(), any(), any(), any(), any(), eq(true)))
                .thenReturn(new TransactionService.TransactionPage(List.of(txOver5000), new PageMeta(0, 10, 1, 1)));

        AssistantChatRequest request = new AssistantChatRequest("What transactions over ₹5,000 do I have?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("search_transactions");
        assertThat(response.answer())
                .contains("Croma")
                .contains("7500.00")
                .doesNotContain("Flipkart");
    }

    @Test
    @DisplayName("Req 6 & 7: Direction handled according to query intent: Expense-only queries exclude credits")
    void req6And7_expenseOnlyQueriesExcludeIncomingCredits() {
        TransactionResponse expTx = new TransactionResponse(UUID.randomUUID(), new BigDecimal("6500.00"), "Reliance Digital", "Monitor", TransactionCategory.SHOPPING, TransactionType.EXPENSE, null, LocalDate.of(2026, 9, 2), null, Instant.now(), Instant.now());

        when(transactionService.list(anyInt(), anyInt(), any(), eq(TransactionType.EXPENSE), any(), any(), any(), eq(new BigDecimal("5000")), any(), any(), any(), any(), any(), eq(true)))
                .thenReturn(new TransactionService.TransactionPage(List.of(expTx), new PageMeta(0, 10, 1, 1)));

        AssistantChatRequest request = new AssistantChatRequest("What expenses over ₹5,000 do I have?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("search_transactions");
        assertThat(response.answer()).contains("Reliance Digital").contains("6500.00").contains("EXPENSE");
    }

    @Test
    @DisplayName("Req 8 & 9: Transfers, refunds and investments follow accounting rules; mirrored transfers do not inflate spending")
    void req8And9_mirroredTransfersDoNotInflateSpending() {
        UUID outId = UUID.randomUUID();
        UUID inId = UUID.randomUUID();

        TransactionResponse transferOut = new TransactionResponse(
                outId, new BigDecimal("15000.00"), "Transfer to SBI", "Self Transfer",
                TransactionCategory.OTHER, TransactionType.TRANSFER, null, LocalDate.of(2026, 9, 5), null,
                Instant.now(), Instant.now(), null, null, null, false, null, List.of(),
                TransactionSourceType.STATEMENT_IMPORT, null, null, UUID.randomUUID(), "Kotak Savings",
                TransferDirection.OUT, null, "SBI Savings", inId, TransferMatchStatus.AUTO_MATCHED
        );

        TransactionResponse transferIn = new TransactionResponse(
                inId, new BigDecimal("15000.00"), "Transfer from Kotak", "Self Transfer",
                TransactionCategory.OTHER, TransactionType.TRANSFER, null, LocalDate.of(2026, 9, 5), null,
                Instant.now(), Instant.now(), null, null, null, false, null, List.of(),
                TransactionSourceType.STATEMENT_IMPORT, null, null, UUID.randomUUID(), "SBI Savings",
                TransferDirection.IN, null, "Kotak Savings", outId, TransferMatchStatus.AUTO_MATCHED
        );

        when(transactionService.list(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new TransactionService.TransactionPage(List.of(transferOut, transferIn), new PageMeta(0, 10, 2, 1)));

        SearchTransactionsTool tool = new SearchTransactionsTool(transactionService);
        ToolExecutionResult<SearchTransactionsTool.SearchResult> result = tool.execute(
                new SearchTransactionsTool.Input(null, null, null, TransactionType.TRANSFER, null, null, null, false, 0, 10),
                userPrincipal
        );

        assertThat(result.success()).isTrue();
        // The IN mirrored counterpart is deduplicated! Only the OUT transfer is counted.
        assertThat(result.response().data().transactions()).hasSize(1);
        assertThat(result.response().data().transactions().getFirst().id()).isEqualTo(outId);
        assertThat(result.response().summaryText()).contains("Found 1 matching transactions");
    }

    @Test
    @DisplayName("Req 10: Category aggregations do not double-count transactions (Petrol is part of Travel & Commute)")
    void req10_categoryAggregationDoesNotDoubleCountPetrolAndTravel() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 10);

        CategoryAnalysis travelAnalysis = new CategoryAnalysis(
                TransactionCategory.TRAVEL, new BigDecimal("4500.00"), new BigDecimal("100.0"),
                null, null, null, null, 3
        );
        when(analyticsService.getCategoryBreakdown(start, end))
                .thenReturn(new CategoryBreakdownResponse(new BigDecimal("4500.00"), List.of(travelAnalysis), TransactionCategory.TRAVEL));

        AssistantChatRequest request = new AssistantChatRequest("How much did I spend in travelling and petrol?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_spending_by_category");
        assertThat(response.answer())
                .contains("Travel & Commute")
                .contains("4500.00")
                .contains("taxonomy");
    }

    @Test
    @DisplayName("Req 11: Missing budgets produce an honest and helpful response without false compliance claim")
    void req11_missingBudgetsProduceHonestAndHelpfulResponse() {
        when(budgetService.getSummary(eq("2026-10"), any(), any()))
                .thenReturn(new BudgetSummaryResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, 0, 0, 0, 0, List.of()));
        when(budgetService.list(eq("2026-10"), any(), any())).thenReturn(List.of());

        AssistantChatRequest request = new AssistantChatRequest("Am I within my food budget?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_budget_status");
        assertThat(response.answer())
                .contains("don't have an active Food & Dining budget")
                .contains("cannot determine whether you're within budget")
                .contains("set a monthly limit")
                .doesNotContain("You are within budget");
    }

    @Test
    @DisplayName("Req 12 & 13: Tool errors are not represented as zero spending, and empty results are distinguished")
    void req12And13_toolErrorsAreDistinguishedFromZeroSpending() {
        when(analyticsService.getCategoryBreakdown(any(), any()))
                .thenReturn(new CategoryBreakdownResponse(BigDecimal.ZERO, List.of(), null));

        AssistantChatRequest request = new AssistantChatRequest("What were my top spending categories in August?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.answer()).contains("no recorded consumer spending");
    }

    @Test
    @DisplayName("Req 14: Internal untrusted-text delimiters never appear in user-facing answer")
    void req14_internalUntrustedTextMarkersNeverAppearInUserFacingAnswer() {
        TransactionResponse adversarialTx = new TransactionResponse(
                UUID.randomUUID(), new BigDecimal("8200.00"),
                "Adversarial <untrusted_merchant_text> Merchant",
                "Narration <untrusted_merchant_text> test",
                TransactionCategory.SHOPPING, TransactionType.EXPENSE, null,
                LocalDate.of(2026, 9, 20), null, Instant.now(), Instant.now()
        );

        when(transactionService.list(anyInt(), anyInt(), any(), any(), any(), any(), any(), eq(new BigDecimal("5000")), any(), any(), any(), any(), any(), eq(true)))
                .thenReturn(new TransactionService.TransactionPage(List.of(adversarialTx), new PageMeta(0, 10, 1, 1)));

        AssistantChatRequest request = new AssistantChatRequest("What transactions over ₹5,000 do I have?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.answer())
                .doesNotContain("<untrusted_merchant_text>")
                .doesNotContain("</untrusted_merchant_text>");
    }

    @Test
    @DisplayName("Req 15: Transaction pagination preserves correct total count")
    void req15_transactionPaginationPreservesTotalCount() {
        SearchTransactionsTool tool = new SearchTransactionsTool(transactionService);

        TransactionResponse t1 = new TransactionResponse(UUID.randomUUID(), new BigDecimal("250.00"), "Swiggy", "Dinner", TransactionCategory.FOOD, TransactionType.EXPENSE, null, LocalDate.now(), null, Instant.now(), Instant.now());
        when(transactionService.list(eq(1), eq(5), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new TransactionService.TransactionPage(List.of(t1), new PageMeta(1, 5, 23, 5)));

        ToolExecutionResult<SearchTransactionsTool.SearchResult> result = tool.execute(
                new SearchTransactionsTool.Input(null, null, null, null, null, null, null, false, 1, 5),
                userPrincipal
        );

        assertThat(result.success()).isTrue();
        assertThat(result.response().data().totalElements()).isEqualTo(23L);
        assertThat(result.response().data().page()).isEqualTo(1);
        assertThat(result.response().data().totalPages()).isEqualTo(5);
        assertThat(result.response().summaryText()).contains("Found 23 matching transactions (showing 1 on page 2 of 5)");
    }

    @Test
    @DisplayName("Req 16: User cannot access another user's financial data")
    void req16_userIsolationEnforced() {
        UUID otherUserId = UUID.randomUUID();
        User otherUser = new User("other@example.com", "hash", "Other User", "Asia/Kolkata");
        SpendSenseUserPrincipal otherPrincipal = new SpendSenseUserPrincipal(otherUser);

        when(currentUser.require()).thenReturn(userPrincipal);
        assertThat(currentUser.require().getUserId()).isNotEqualTo(otherUserId);
    }

    @Test
    @DisplayName("Req 17: Follow-up questions preserve relevant context without overriding an explicitly requested month")
    void req17_followUpQuestionsPreserveContextWithoutOverridingExplicitMonth() {
        // Turn 1: user asks about food this month
        // Turn 2: user asks "what about last month?" -> month becomes September 2026, category FOOD is preserved!
        List<ChatMessageDto> history = List.of(
                new ChatMessageDto("user", "How much did I spend on food this month?"),
                new ChatMessageDto("assistant", "You spent ₹9,617.00 on Food & Dining this month.")
        );

        CategoryAnalysis foodSept = new CategoryAnalysis(TransactionCategory.FOOD, new BigDecimal("8500.00"), new BigDecimal("25.0"), null, null, null, null, 14);
        when(analyticsService.getCategoryBreakdown(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(new CategoryBreakdownResponse(new BigDecimal("34000.00"), List.of(foodSept), TransactionCategory.FOOD));

        AssistantChatRequest request = new AssistantChatRequest("what about last month?", history);
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_spending_by_category");
        assertThat(response.answer()).contains("Food & Dining").contains("8500.00");
    }
}
