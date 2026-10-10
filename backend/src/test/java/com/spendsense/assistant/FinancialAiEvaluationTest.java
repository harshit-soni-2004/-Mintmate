package com.spendsense.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spendsense.account.FinancialAccountService;
import com.spendsense.analytics.AnomalyType;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.AnomalyDetectionService;
import com.spendsense.analytics.TrendDirection;
import com.spendsense.analytics.dto.*;
import com.spendsense.assistant.dto.AssistantChatRequest;
import com.spendsense.assistant.dto.AssistantChatResponse;
import com.spendsense.assistant.dto.ChatMessageDto;
import com.spendsense.assistant.context.FinancialContextService;
import com.spendsense.assistant.model.provider.MockModelGateway;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolDispatcher;
import com.spendsense.assistant.tool.ToolRegistry;
import com.spendsense.assistant.tool.impl.*;
import com.spendsense.budget.BudgetService;
import com.spendsense.goal.GoalService;
import com.spendsense.goal.dto.GoalResponse;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionService;
import com.spendsense.transaction.TransactionType;
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

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Phase 8A AI Financial Foundation Evaluation Suite.
 * Runs 50 realistic financial scenarios across 5 distinct categories:
 * - 10 Basic Retrieval
 * - 10 Comparison & Analysis
 * - 10 Spending Trends
 * - 10 Savings & Budgets
 * - 10 Conversational Follow-ups
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialAiEvaluationTest {

    @Mock private CurrentUser currentUser;
    @Mock private AnalyticsService analyticsService;
    @Mock private AnomalyDetectionService anomalyDetectionService;
    @Mock private GoalService goalService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetService budgetService;
    @Mock private TransactionService transactionService;
    @Mock private FinancialAccountService accountService;
    @Mock private com.spendsense.assistant.action.PendingActionService pendingActionService;
    @Mock private com.spendsense.assistant.proactive.ProactiveInsightService proactiveInsightService;

    private AssistantOrchestrator orchestrator;
    private ObjectMapper objectMapper;
    private List<EvaluationScenario> scenarios;

    record EvaluationScenario(
            String id,
            String category,
            String question,
            String expectedIntent,
            List<String> expectedTools,
            List<String> requiredFacts,
            List<String> forbiddenInventions
    ) {}

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        // Set up authenticated test user principal
        User testUser = new User("harshit@example.com", "hash", "Harshit Soni", "Asia/Kolkata");
        SpendSenseUserPrincipal principal = new SpendSenseUserPrincipal(testUser);
        when(currentUser.require()).thenReturn(principal);

        when(pendingActionService.createPendingAction(any(), any(), any()))
                .thenAnswer(inv -> new com.spendsense.assistant.dto.PendingActionDto(
                        UUID.randomUUID(),
                        inv.getArgument(0),
                        inv.getArgument(2),
                        Map.of(),
                        Instant.now().plusSeconds(900)
                ));

        when(proactiveInsightService.getActiveInsights())
                .thenReturn(List.of(
                        new com.spendsense.assistant.proactive.ProactiveInsightDto(
                                UUID.randomUUID(),
                                com.spendsense.assistant.proactive.ProactiveInsightType.CATEGORY_SPIKE,
                                com.spendsense.analytics.InsightSeverity.WARNING,
                                "Food spending is up 35%",
                                "Food spend elevated",
                                Map.of("category", "FOOD"),
                                "Review discretionary food spend",
                                "CATEGORY_SPIKE:FOOD:2026-09",
                                Instant.now(),
                                Instant.now().plusSeconds(86400)
                        )
                ));

        // Mock deterministic spending summary
        SpendingSummaryResponse mockSummary = new SpendingSummaryResponse(
                new BigDecimal("100000.00"),
                new BigDecimal("35000.00"),
                new BigDecimal("65000.00"),
                new BigDecimal("65.0"),
                20L,
                new BigDecimal("1750.00"),
                new BigDecimal("8000.00"),
                LocalDate.now().withDayOfMonth(1),
                LocalDate.now(),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        when(analyticsService.getSummary(any(), any())).thenReturn(mockSummary);

        // Mock deterministic period comparison
        PeriodComparisonResponse mockComparison = new PeriodComparisonResponse(
                LocalDate.now().withDayOfMonth(1), LocalDate.now(),
                LocalDate.now().minusMonths(1).withDayOfMonth(1), LocalDate.now().minusMonths(1).withDayOfMonth(28),
                new TrendMetric(new BigDecimal("100000.00"), new BigDecimal("95000.00"), new BigDecimal("5000.00"), new BigDecimal("5.3"), TrendDirection.INCREASED),
                new TrendMetric(new BigDecimal("35000.00"), new BigDecimal("32000.00"), new BigDecimal("3000.00"), new BigDecimal("9.4"), TrendDirection.INCREASED),
                new TrendMetric(new BigDecimal("65000.00"), new BigDecimal("63000.00"), new BigDecimal("2000.00"), new BigDecimal("3.2"), TrendDirection.INCREASED),
                new BigDecimal("65.0"),
                new BigDecimal("66.3"),
                new BigDecimal("-1.3"),
                List.of(
                        new CategoryComparison("Food", new BigDecimal("12000"), new BigDecimal("9000"), new BigDecimal("3000"), new BigDecimal("33.3")),
                        new CategoryComparison("Travel", new BigDecimal("8000"), new BigDecimal("6000"), new BigDecimal("2000"), new BigDecimal("33.3"))
                ),
                List.of(new CategoryComparison("Food", new BigDecimal("12000"), new BigDecimal("9000"), new BigDecimal("3000"), new BigDecimal("33.3"))),
                List.of()
        );
        when(analyticsService.getComparison(any(), any())).thenReturn(mockComparison);

        // Mock category breakdown
        CategoryBreakdownResponse mockBreakdown = new CategoryBreakdownResponse(
                new BigDecimal("35000.00"),
                List.of(
                        new CategoryAnalysis(TransactionCategory.FOOD, new BigDecimal("12000.00"), new BigDecimal("34.3"), new BigDecimal("9000.00"), new BigDecimal("3000.00"), new BigDecimal("33.3"), TrendDirection.INCREASED, 12L),
                        new CategoryAnalysis(TransactionCategory.TRAVEL, new BigDecimal("8000.00"), new BigDecimal("22.9"), new BigDecimal("6000.00"), new BigDecimal("2000.00"), new BigDecimal("33.3"), TrendDirection.INCREASED, 6L),
                        new CategoryAnalysis(TransactionCategory.SHOPPING, new BigDecimal("7000.00"), new BigDecimal("20.0"), new BigDecimal("7500.00"), new BigDecimal("-500.00"), new BigDecimal("-6.7"), TrendDirection.DECREASED, 4L)
                ),
                TransactionCategory.FOOD
        );
        when(analyticsService.getCategoryBreakdown(any(), any())).thenReturn(mockBreakdown);

        when(analyticsService.getTrends(any(), any(), any())).thenAnswer(invocation -> {
            com.spendsense.analytics.AggregationPeriod period = invocation.getArgument(0);
            LocalDate from = invocation.getArgument(1);
            LocalDate to = invocation.getArgument(2);
            return new SpendingTrendResponse(
                    period != null ? period : com.spendsense.analytics.AggregationPeriod.MONTHLY,
                    List.of(
                            new SpendingTrendPoint("2026-05", LocalDate.of(2026, 5, 1), new BigDecimal("28000.00"), new BigDecimal("90000.00"), 18L),
                            new SpendingTrendPoint("2026-06", LocalDate.of(2026, 6, 1), new BigDecimal("31000.00"), new BigDecimal("95000.00"), 22L),
                            new SpendingTrendPoint("2026-07", LocalDate.of(2026, 7, 1), new BigDecimal("33000.00"), new BigDecimal("98000.00"), 24L),
                            new SpendingTrendPoint("2026-08", LocalDate.of(2026, 8, 1), new BigDecimal("32000.00"), new BigDecimal("100000.00"), 21L),
                            new SpendingTrendPoint("2026-09", LocalDate.of(2026, 9, 1), new BigDecimal("35000.00"), new BigDecimal("100000.00"), 20L)
                    ),
                    from != null ? from : LocalDate.of(2026, 5, 1),
                    to != null ? to : LocalDate.of(2026, 9, 30)
            );
        });

        // Mock anomalies
        List<SpendingAnomaly> mockAnomalies = List.of(
                new SpendingAnomaly(
                        "anom-1",
                        AnomalyType.LARGE_TRANSACTION,
                        "Unusually large transaction at Apple Store",
                        "Payment of ₹45,000 at Apple Store is 3.5x your average transaction size (₹1,750).",
                        "Transaction exceeds 3.0x average expense threshold.",
                        new BigDecimal("45000.00"),
                        new BigDecimal("1750.00"),
                        new BigDecimal("3.5"),
                        TransactionCategory.SHOPPING,
                        LocalDate.now(),
                        UUID.randomUUID()
                )
        );
        when(anomalyDetectionService.detectAnomalies(any(), any())).thenReturn(mockAnomalies);

        // Mock goals
        List<GoalResponse> mockGoals = List.of(
                new GoalResponse(
                        UUID.randomUUID(),
                        "Emergency Fund",
                        new BigDecimal("200000.00"),
                        new BigDecimal("84000.00"),
                        new BigDecimal("116000.00"),
                        42.0,
                        false,
                        LocalDate.of(2027, 3, 31),
                        Instant.now(),
                        Instant.now()
                )
        );
        when(goalService.list()).thenReturn(mockGoals);

        // Mock transactions
        Transaction tx1 = new Transaction(testUser, new BigDecimal("999.00"), "Netflix", TransactionCategory.ENTERTAINMENT, TransactionType.EXPENSE, LocalDate.now().minusDays(30), null);
        Transaction tx2 = new Transaction(testUser, new BigDecimal("999.00"), "Netflix", TransactionCategory.ENTERTAINMENT, TransactionType.EXPENSE, LocalDate.now().minusDays(60), null);
        Transaction tx3 = new Transaction(testUser, new BigDecimal("999.00"), "Netflix", TransactionCategory.ENTERTAINMENT, TransactionType.EXPENSE, LocalDate.now().minusDays(90), null);

        when(transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(any(), any(), any(), any()))
                .thenReturn(List.of(tx1, tx2, tx3));

        when(transactionRepository.countByUserIdAndTypeAndDateBetween(any(), any(), any(), any()))
                .thenReturn(3L);

        when(transactionRepository.findById(any()))
                .thenReturn(Optional.of(tx1));

        // Mock transactionService.list for SearchTransactionsTool
        com.spendsense.transaction.dto.TransactionResponse sampleTx = new com.spendsense.transaction.dto.TransactionResponse(
                UUID.randomUUID(),
                new BigDecimal("1200.00"),
                "Amazon",
                "Electronics purchase",
                TransactionCategory.SHOPPING,
                TransactionType.EXPENSE,
                null,
                LocalDate.now().minusDays(5),
                null,
                Instant.now(),
                Instant.now()
        );
        when(transactionService.list(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new TransactionService.TransactionPage(List.of(sampleTx), new com.spendsense.common.api.PageMeta(0, 10, 1, 1)));

        // Mock budgetService for GetBudgetStatusTool
        com.spendsense.budget.dto.BudgetResponse sampleBudget = new com.spendsense.budget.dto.BudgetResponse(
                UUID.randomUUID(),
                TransactionCategory.FOOD,
                new BigDecimal("10000.00"),
                com.spendsense.budget.BudgetPeriod.MONTHLY,
                LocalDate.now().withDayOfMonth(1),
                LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),
                new BigDecimal("6000.00"),
                new BigDecimal("4000.00"),
                60.0,
                15,
                com.spendsense.budget.BudgetStatus.ON_TRACK,
                Instant.now(), Instant.now()
        );
        com.spendsense.budget.dto.BudgetResponse sampleTravelBudget = new com.spendsense.budget.dto.BudgetResponse(
                UUID.randomUUID(),
                TransactionCategory.TRAVEL,
                new BigDecimal("15000.00"),
                com.spendsense.budget.BudgetPeriod.MONTHLY,
                LocalDate.now().withDayOfMonth(1),
                LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),
                new BigDecimal("8000.00"),
                new BigDecimal("7000.00"),
                53.3,
                10,
                com.spendsense.budget.BudgetStatus.ON_TRACK,
                Instant.now(), Instant.now()
        );
        com.spendsense.budget.dto.BudgetSummaryResponse sampleBudgetSummary = new com.spendsense.budget.dto.BudgetSummaryResponse(
                new BigDecimal("25000.00"),
                new BigDecimal("14000.00"),
                new BigDecimal("11000.00"),
                56.0,
                2, 0, 0, 2,
                List.of(sampleBudget, sampleTravelBudget)
        );
        when(budgetService.getSummary(any(), any(), any())).thenReturn(sampleBudgetSummary);
        when(budgetService.list(any(), any(), any())).thenReturn(List.of(sampleBudget, sampleTravelBudget));

        // Mock accountService for GetFinancialAccountsTool
        com.spendsense.account.dto.FinancialAccountResponse sampleAccount = new com.spendsense.account.dto.FinancialAccountResponse(
                UUID.randomUUID(),
                "Primary Checking",
                "HDFC Bank",
                com.spendsense.document.DocumentAccountType.SAVINGS,
                "4821",
                "INR",
                true,
                true,
                Instant.now(),
                Instant.now()
        );
        when(accountService.list()).thenReturn(List.of(sampleAccount));

        FinancialContextService contextService = new FinancialContextService(analyticsService, budgetService, goalService, transactionRepository);

        // Assemble tool suite
        List<FinancialTool<?, ?>> tools = List.<FinancialTool<?, ?>>of(
                new GetSpendingSummaryTool(analyticsService),
                new GetSpendingByCategoryTool(analyticsService),
                new CompareSpendingPeriodsTool(analyticsService),
                new GetSpendingTrendTool(analyticsService),
                new GetIncomeSummaryTool(analyticsService, transactionRepository),
                new GetSavingsSummaryTool(analyticsService),
                new AnalyzeSpendingChangeTool(analyticsService),
                new IdentifyAnomaliesTool(anomalyDetectionService),
                new GetGoalStatusTool(goalService),
                new GetRecurringExpensesTool(transactionRepository),
                new GetBudgetStatusTool(budgetService),
                new SearchTransactionsTool(transactionService),
                new GetFinancialAccountsTool(accountService),
                new CalculateBudgetRecommendationTool(contextService),
                new CalculateSavingsTargetTool(contextService),
                new GetSpendingFlexibilityTool(contextService),
                new GetCategoryInsightsTool(contextService),
                new GetFinancialContextTool(contextService),
                new com.spendsense.assistant.tool.impl.mutation.CreateOrUpdateBudgetTool(budgetService, pendingActionService, objectMapper),
                new com.spendsense.assistant.tool.impl.mutation.CreateOrUpdateGoalTool(goalService, pendingActionService, objectMapper),
                new com.spendsense.assistant.tool.impl.mutation.UpdateTransactionCategoryTool(transactionRepository, transactionService, pendingActionService, objectMapper),
                new com.spendsense.assistant.tool.impl.GetProactiveInsightsTool(proactiveInsightService)
        );

        ToolRegistry registry = new ToolRegistry(tools);
        ToolDispatcher dispatcher = new ToolDispatcher(registry, currentUser, objectMapper, Validation.buildDefaultValidatorFactory().getValidator());
        MockModelGateway modelGateway = new MockModelGateway(objectMapper);
        orchestrator = new AssistantOrchestrator(modelGateway, registry, dispatcher, objectMapper);

        // Load scenario dataset from resources
        try (InputStream is = getClass().getResourceAsStream("/ai_financial_evaluation_set.json")) {
            assertThat(is).as("ai_financial_evaluation_set.json must exist in test resources").isNotNull();
            scenarios = objectMapper.readValue(is, new TypeReference<List<EvaluationScenario>>() {});
        }
        assertThat(scenarios).hasSize(150);
    }

    @Test
    @DisplayName("Evaluate complete Phase 8 comprehensive 150-scenario dataset")
    void evaluateAllScenarios() {
        int passed = 0;

        for (EvaluationScenario sc : scenarios) {
            List<ChatMessageDto> history = new ArrayList<>();
            // For follow-up scenarios (FOL-02 through FOL-04, FOL-06, etc.), provide contextual history
            if (sc.id().equals("FOL-02") || sc.id().equals("FOL-03") || sc.id().equals("FOL-04") || sc.id().equals("FOL-11")) {
                history.add(new ChatMessageDto("user", "How much did I spend on food this month?"));
                history.add(new ChatMessageDto("assistant", "You spent ₹12,000 on Food this month."));
            } else if (sc.id().equals("FOL-06") || sc.id().equals("FOL-07")) {
                history.add(new ChatMessageDto("user", "How much did I spend on travel this month?"));
                history.add(new ChatMessageDto("assistant", "You spent ₹8,000 on Travel this month."));
            } else if (sc.id().equals("FOL-09") || sc.id().equals("FOL-10")) {
                history.add(new ChatMessageDto("user", "How much did I spend overall this month?"));
                history.add(new ChatMessageDto("assistant", "You spent ₹35,000 in consumer expenses this month."));
            }

            AssistantChatRequest request = new AssistantChatRequest(sc.question(), history);
            AssistantChatResponse response = orchestrator.processChat(request);

            // 1. Verify expected tool was executed
            assertThat(response.executedTools())
                    .as("Scenario %s (%s) must execute expected tool", sc.id(), sc.question())
                    .isNotEmpty();

            String executedToolName = response.executedTools().getFirst().toolName();
            assertThat(sc.expectedTools())
                    .as("Scenario %s executed unexpected tool '%s'", sc.id(), executedToolName)
                    .contains(executedToolName);

            // 2. Verify answer contains required facts
            String fullText = (response.answer() + " " + response.executedTools().getFirst().summary()).toLowerCase();
            for (String requiredFact : sc.requiredFacts()) {
                assertThat(fullText)
                        .as("Scenario %s response must ground required fact '%s'", sc.id(), requiredFact)
                        .contains(requiredFact.toLowerCase());
            }

            // 3. Verify forbidden inventions do NOT appear (e.g. raw SQL keywords, exception stacktraces, or psychological labels)
            assertThat(fullText)
                    .as("Scenario %s leaked forbidden SQL syntax or technical internals", sc.id())
                    .doesNotContain("select * from")
                    .doesNotContain("nullpointerexception")
                    .doesNotContain("sqlexception")
                    .doesNotContain("impulsive")
                    .doesNotContain("irresponsible")
                    .doesNotContain("bad with money")
                    .doesNotContain("financially unhealthy");

            passed++;
        }

        assertThat(passed).isEqualTo(150);
    }
}

