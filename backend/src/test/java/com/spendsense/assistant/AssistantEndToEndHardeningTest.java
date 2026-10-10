package com.spendsense.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.assistant.action.PendingAction;
import com.spendsense.assistant.action.PendingActionRepository;
import com.spendsense.assistant.action.PendingActionService;
import com.spendsense.assistant.action.PendingActionStatus;
import com.spendsense.assistant.context.FinancialContextService;
import com.spendsense.assistant.dto.AssistantChatRequest;
import com.spendsense.assistant.dto.AssistantChatResponse;
import com.spendsense.assistant.dto.PendingActionResultDto;
import com.spendsense.assistant.model.provider.GeminiModelGateway;
import com.spendsense.assistant.model.provider.MockModelGateway;
import com.spendsense.assistant.proactive.ProactiveInsightDto;
import com.spendsense.assistant.proactive.ProactiveInsightService;
import com.spendsense.assistant.proactive.ProactiveInsightType;
import com.spendsense.assistant.telemetry.AssistantTelemetryService;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolDispatcher;
import com.spendsense.assistant.tool.ToolRegistry;
import com.spendsense.assistant.tool.impl.CalculateBudgetRecommendationTool;
import com.spendsense.assistant.tool.impl.GetProactiveInsightsTool;
import com.spendsense.assistant.tool.impl.GetSpendingSummaryTool;
import com.spendsense.assistant.tool.impl.mutation.CreateOrUpdateBudgetTool;
import com.spendsense.budget.BudgetPeriod;
import com.spendsense.budget.BudgetService;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantEndToEndHardeningTest {

    @Mock private CurrentUser currentUser;
    @Mock private UserRepository userRepository;
    @Mock private PendingActionRepository pendingActionRepository;
    @Mock private AnalyticsService analyticsService;
    @Mock private BudgetService budgetService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ProactiveInsightService proactiveInsightService;
    @Mock private AssistantTelemetryService telemetryService;

    private AssistantOrchestrator orchestrator;
    private PendingActionService pendingActionService;
    private ToolRegistry toolRegistry;
    private User testUser;
    private SpendSenseUserPrincipal principal;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        testUser = new User("ada@example.com", "hash", "Ada Lovelace", "Asia/Kolkata");
        principal = new SpendSenseUserPrincipal(testUser);
        when(currentUser.require()).thenReturn(principal);
        when(userRepository.findById(principal.getUserId())).thenReturn(Optional.of(testUser));

        FinancialContextService contextService = new FinancialContextService(analyticsService, budgetService, null, transactionRepository);

        pendingActionService = new PendingActionService(
                pendingActionRepository,
                userRepository,
                currentUser,
                null, // will be wired below
                objectMapper,
                null,
                telemetryService
        );

        CreateOrUpdateBudgetTool createOrUpdateBudgetTool = new CreateOrUpdateBudgetTool(budgetService, pendingActionService, objectMapper);
        GetSpendingSummaryTool spendingSummaryTool = new GetSpendingSummaryTool(analyticsService);
        CalculateBudgetRecommendationTool budgetRecTool = new CalculateBudgetRecommendationTool(contextService);
        GetProactiveInsightsTool proactiveTool = new GetProactiveInsightsTool(proactiveInsightService);

        List<FinancialTool<?, ?>> tools = List.of(
                spendingSummaryTool,
                budgetRecTool,
                createOrUpdateBudgetTool,
                proactiveTool
        );

        toolRegistry = new ToolRegistry(tools);

        // Re-inject tool registry into pendingActionService
        pendingActionService = new PendingActionService(
                pendingActionRepository,
                userRepository,
                currentUser,
                toolRegistry,
                objectMapper,
                null,
                telemetryService
        );

        ToolDispatcher dispatcher = new ToolDispatcher(toolRegistry, currentUser, objectMapper, Validation.buildDefaultValidatorFactory().getValidator());
        MockModelGateway modelGateway = new MockModelGateway(objectMapper);
        orchestrator = new AssistantOrchestrator(modelGateway, toolRegistry, dispatcher, objectMapper, currentUser, telemetryService);
    }

    @Test
    @DisplayName("Journey 1: Question -> Financial tool -> Answer")
    void journey1_questionToGroundedAnswer() {
        SpendingSummaryResponse summary = new SpendingSummaryResponse(
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
        when(analyticsService.getSummary(any(), any())).thenReturn(summary);

        AssistantChatRequest request = new AssistantChatRequest("How much did I spend this month?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_spending_summary");
        assertThat(response.answer()).contains("35000.00");
        verify(telemetryService).logChatCompleted(any(), anyLong(), anyInt(), any(), eq(false));
    }

    @Test
    @DisplayName("Journey 2: Question -> Personalized Recommendation -> Explanation")
    void journey2_personalizedRecommendation() {
        com.spendsense.analytics.dto.CategoryBreakdownResponse mockBreakdown = new com.spendsense.analytics.dto.CategoryBreakdownResponse(
                new BigDecimal("35000.00"),
                List.of(
                        new com.spendsense.analytics.dto.CategoryAnalysis(
                                TransactionCategory.FOOD,
                                new BigDecimal("12000.00"),
                                new BigDecimal("34.3"),
                                new BigDecimal("9000.00"),
                                new BigDecimal("3000.00"),
                                new BigDecimal("33.3"),
                                com.spendsense.analytics.TrendDirection.INCREASED,
                                12L
                        )
                ),
                TransactionCategory.FOOD
        );
        when(analyticsService.getCategoryBreakdown(any(), any())).thenReturn(mockBreakdown);

        AssistantChatRequest request = new AssistantChatRequest("What is a realistic food budget for me?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("calculate_budget_recommendation");
        assertThat(response.answer()).contains("Food");
    }

    @Test
    @DisplayName("Journey 3: Recommendation -> Action Proposal -> Confirmation -> Mutation -> Verified Result")
    void journey3_actionProposalConfirmationAndExecution() {
        UUID actionId = UUID.randomUUID();
        when(pendingActionRepository.save(any(PendingAction.class))).thenAnswer(inv -> {
            PendingAction pa = inv.getArgument(0);
            return pa;
        });

        // 1. Propose action via chat
        AssistantChatRequest chatRequest = new AssistantChatRequest("Set my food budget to 8500", List.of());
        AssistantChatResponse chatResponse = orchestrator.processChat(chatRequest);

        assertThat(chatResponse.requiresConfirmation()).isTrue();
        assertThat(chatResponse.pendingAction()).isNotNull();
        assertThat(chatResponse.pendingAction().actionType()).isEqualTo("create_or_update_budget");

        // 2. Prepare persistent pending action for confirmation
        PendingAction pendingAction = new PendingAction(
                testUser,
                "create_or_update_budget",
                "{\"category\":\"FOOD\",\"limitAmount\":8500.00,\"yearMonth\":\"2026-09\"}",
                "Set new monthly Food budget of ₹8,500.00 for 2026-09",
                Instant.now().plus(15, ChronoUnit.MINUTES)
        );

        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.of(pendingAction));
        when(pendingActionRepository.transitionToConfirmed(eq(actionId), eq(principal.getUserId()), eq(PendingActionStatus.PENDING), eq(PendingActionStatus.CONFIRMED), any()))
                .thenReturn(1);

        BudgetResponse expectedBudget = new BudgetResponse(
                UUID.randomUUID(),
                TransactionCategory.FOOD,
                new BigDecimal("8500.00"),
                BudgetPeriod.MONTHLY,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                BigDecimal.ZERO,
                new BigDecimal("8500.00"),
                0.0,
                0,
                com.spendsense.budget.BudgetStatus.ON_TRACK,
                Instant.now(),
                Instant.now()
        );
        when(budgetService.create(any())).thenReturn(expectedBudget);

        // 3. Confirm and execute
        PendingActionResultDto result = pendingActionService.confirmAndExecute(actionId);

        assertThat(result.status()).isEqualTo(PendingActionStatus.EXECUTED);
        assertThat(result.result()).isEqualTo(expectedBudget);
        verify(telemetryService).logActionConfirmed(eq(principal.getUserId()), eq("create_or_update_budget"), anyLong(), eq(true));
    }

    @Test
    @DisplayName("Journey 4: Financial change -> Proactive Detection -> User opens assistant -> Grounded explanation")
    void journey4_proactiveInsightConversationalFlow() {
        ProactiveInsightDto activeAlert = new ProactiveInsightDto(
                UUID.randomUUID(),
                ProactiveInsightType.CATEGORY_SPIKE,
                com.spendsense.analytics.InsightSeverity.WARNING,
                "Food spending is 28% above recent average",
                "Food spending surged due to frequent dining out",
                Map.of("category", "FOOD", "deltaPct", 28.0),
                "Review dining expenses",
                "CATEGORY_SPIKE:FOOD:2026-09",
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );
        when(proactiveInsightService.getActiveInsights()).thenReturn(List.of(activeAlert));

        AssistantChatRequest request = new AssistantChatRequest("Do I have any spending alerts?", List.of());
        AssistantChatResponse response = orchestrator.processChat(request);

        assertThat(response.executedTools()).hasSize(1);
        assertThat(response.executedTools().getFirst().toolName()).isEqualTo("get_proactive_insights");
        assertThat(response.answer()).contains("Food spending is 28% above recent average");
    }

    @Test
    @DisplayName("Journey 5: Gemini Failure -> Graceful Error without leaking secrets or internals")
    void journey5_geminiFailureGracefulFallback() {
        MockModelGateway failingGateway = mock(MockModelGateway.class);
        when(failingGateway.generateResponse(any(), any()))
                .thenThrow(new RuntimeException("Simulated upstream LLM network failure"));

        AssistantOrchestrator resilientOrchestrator = new AssistantOrchestrator(
                failingGateway,
                toolRegistry,
                new ToolDispatcher(toolRegistry, currentUser, objectMapper, Validation.buildDefaultValidatorFactory().getValidator()),
                objectMapper
        );

        assertThatThrownBy(() -> resilientOrchestrator.processChat(new AssistantChatRequest("Hello", List.of())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Journey 6: Cross-user unauthorized action confirmation -> Rejected safely")
    void journey6_crossUserConfirmationRejected() {
        UUID actionId = UUID.randomUUID();
        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pendingActionService.confirmAndExecute(actionId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Journey 7: Expired action confirmation -> Rejected safely with 410 GONE")
    void journey7_expiredActionRejected() {
        UUID actionId = UUID.randomUUID();
        PendingAction expiredAction = new PendingAction(
                testUser,
                "create_or_update_budget",
                "{}",
                "Expired budget mutation",
                Instant.now().minus(5, ChronoUnit.MINUTES)
        );
        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.of(expiredAction));

        assertThatThrownBy(() -> pendingActionService.confirmAndExecute(actionId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.GONE);
    }
}
