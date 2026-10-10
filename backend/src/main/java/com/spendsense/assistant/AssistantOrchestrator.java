package com.spendsense.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.dto.AssistantChatRequest;
import com.spendsense.assistant.dto.AssistantChatResponse;
import com.spendsense.assistant.dto.ChatMessageDto;
import com.spendsense.assistant.dto.PendingActionDto;
import com.spendsense.assistant.dto.ToolCallSummaryDto;
import com.spendsense.assistant.model.*;
import com.spendsense.assistant.telemetry.AssistantTelemetryService;
import com.spendsense.assistant.tool.*;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class AssistantOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AssistantOrchestrator.class);
    private static final int MAX_TOOL_ITERATIONS = 3;

    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;
    private final AssistantTelemetryService telemetryService;

    @Autowired
    public AssistantOrchestrator(
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolDispatcher toolDispatcher,
            ObjectMapper objectMapper,
            @Autowired(required = false) CurrentUser currentUser,
            @Autowired(required = false) AssistantTelemetryService telemetryService
    ) {
        this.modelGateway = modelGateway;
        this.toolRegistry = toolRegistry;
        this.toolDispatcher = toolDispatcher;
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        this.currentUser = currentUser;
        this.telemetryService = telemetryService;
    }

    public AssistantOrchestrator(
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolDispatcher toolDispatcher,
            ObjectMapper objectMapper
    ) {
        this(modelGateway, toolRegistry, toolDispatcher, objectMapper, null, null);
    }

    public AssistantOrchestrator(
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolDispatcher toolDispatcher
    ) {
        this(modelGateway, toolRegistry, toolDispatcher, createDefaultObjectMapper(), null, null);
    }

    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public AssistantChatResponse processChat(AssistantChatRequest request) {
        long startTime = System.currentTimeMillis();
        UUID userId = null;
        if (currentUser != null) {
            try {
                userId = currentUser.require().getUserId();
            } catch (Exception ignored) {}
        }
        AssistantTelemetryService.ChatTelemetryContext telemetryCtx =
                telemetryService != null ? AssistantTelemetryService.ChatTelemetryContext.create(userId) : null;
        List<String> invokedToolNames = new ArrayList<>();

        String systemPrompt = buildSystemPrompt();
        List<AssistantMessage> conversation = new ArrayList<>();

        // Add client-provided history for pronoun / temporal context (stateless on backend)
        if (request.conversationHistory() != null) {
            List<ChatMessageDto> history = request.conversationHistory();
            int startIdx = Math.max(0, history.size() - 20);
            for (int i = startIdx; i < history.size(); i++) {
                ChatMessageDto msg = history.get(i);
                if ("user".equalsIgnoreCase(msg.role())) {
                    conversation.add(AssistantMessage.user(msg.content()));
                } else if ("assistant".equalsIgnoreCase(msg.role())) {
                    conversation.add(AssistantMessage.assistant(msg.content()));
                }
            }
        }

        // Add current user prompt
        conversation.add(AssistantMessage.user(request.message()));

        List<ToolDefinition> availableTools = toolRegistry.getAllToolDefinitions();
        List<ToolCallSummaryDto> executedTools = new ArrayList<>();
        Map<String, ToolExecutionResult<?>> readOnlyResultCache = new HashMap<>();
        PendingActionDto pendingAction = null;
        boolean requiresConfirmation = false;
        int completedIterations = 0;

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            completedIterations = iteration + 1;
            AssistantConversationContext context = new AssistantConversationContext(systemPrompt, conversation);

            long modelStart = System.currentTimeMillis();
            AssistantResponse modelResponse = modelGateway.generateResponse(context, availableTools);
            long modelDuration = System.currentTimeMillis() - modelStart;

            if (telemetryService != null && telemetryCtx != null) {
                telemetryService.logModelCall(
                        telemetryCtx.requestId(),
                        modelGateway.getProviderName(),
                        modelDuration,
                        true,
                        modelResponse.hasToolCalls() ? modelResponse.toolCalls().size() : 0
                );
            }

            if (!modelResponse.hasToolCalls()) {
                String answer = modelResponse.content() != null && !modelResponse.content().isBlank()
                        ? modelResponse.content()
                        : "I have reviewed your financial records, but could not produce a detailed answer.";

                if (telemetryService != null && telemetryCtx != null) {
                    long totalDuration = System.currentTimeMillis() - startTime;
                    telemetryService.logChatCompleted(telemetryCtx, totalDuration, completedIterations, invokedToolNames, requiresConfirmation);
                }

                if (requiresConfirmation && pendingAction != null) {
                    return AssistantChatResponse.confirmationRequired(sanitizeUserFacingAnswer(answer), executedTools, pendingAction);
                }
                return AssistantChatResponse.text(sanitizeUserFacingAnswer(answer), executedTools);
            }

            // Process tool calls
            List<ToolCallRequest> toolCalls = modelResponse.toolCalls();
            conversation.add(AssistantMessage.assistantWithToolCalls(null, toolCalls));

            for (ToolCallRequest call : toolCalls) {
                invokedToolNames.add(call.name());
                boolean isMutation = isMutationTool(call.name());

                // Defensive guard: at most one mutation proposal per conversational turn
                if (requiresConfirmation && isMutation) {
                    log.warn("Skipping additional mutation tool '{}' because an action is already pending confirmation", call.name());
                    conversation.add(AssistantMessage.tool(call.id(), call.name(), "{\"error\": \"Another action is already awaiting user confirmation. Please confirm or cancel the current action first.\"}"));
                    continue;
                }

                String cacheKey = call.name() + ":" + (call.arguments() != null ? call.arguments().toString() : "{}");
                ToolExecutionResult<?> result;
                long toolDuration = 0;

                if (!isMutation && readOnlyResultCache.containsKey(cacheKey)) {
                    log.debug("Reusing cached read-only tool execution result for '{}'", call.name());
                    result = readOnlyResultCache.get(cacheKey);
                } else {
                    log.info("Executing tool: {} with args: {}", call.name(), call.arguments());
                    long toolStart = System.currentTimeMillis();
                    result = toolDispatcher.dispatch(call.name(), call.arguments());
                    toolDuration = System.currentTimeMillis() - toolStart;
                    if (!isMutation && result.isSuccess()) {
                        readOnlyResultCache.put(cacheKey, result);
                    }
                }

                if (telemetryService != null && telemetryCtx != null) {
                    telemetryService.logToolExecution(
                            telemetryCtx.requestId(),
                            call.name(),
                            toolDuration,
                            result.isSuccess(),
                            isMutation
                    );
                }

                String resultJson;
                if (!result.isSuccess() || result.response() == null) {
                    String correlationId = "ASST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
                    log.warn("Tool '{}' execution failed [correlationId={}]: {}", call.name(), correlationId, result.errorMessage());
                    resultJson = String.format("{\"error\": \"I couldn't retrieve your spending data right now. Please try again. Reference: %s\"}", correlationId);
                } else {
                    try {
                        resultJson = objectMapper.writeValueAsString(result.response());
                    } catch (Exception e) {
                        String correlationId = "ASST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
                        log.error("Failed to serialize tool result for tool '{}' [correlationId={}]: {}", call.name(), correlationId, e.getMessage(), e);
                        resultJson = String.format("{\"error\": \"I couldn't retrieve your spending data right now. Please try again. Reference: %s\"}", correlationId);
                    }
                }

                if (result.requiresConfirmation() && result.pendingAction() instanceof PendingActionDto dto) {
                    requiresConfirmation = true;
                    pendingAction = dto;
                }

                String summary = (result.response() != null && result.response().summaryText() != null)
                        ? result.response().summaryText()
                        : (result.errorMessage() != null ? result.errorMessage() : "Executed " + call.name());

                executedTools.add(new ToolCallSummaryDto(call.name(), summary));
                conversation.add(AssistantMessage.tool(call.id(), call.name(), resultJson));
            }
        }

        // If loop limit reached, provide latest grounded text
        String finalAnswer = "I retrieved your financial records. " + (!executedTools.isEmpty() ? executedTools.getLast().summary() : "");

        if (telemetryService != null && telemetryCtx != null) {
            long totalDuration = System.currentTimeMillis() - startTime;
            telemetryService.logChatCompleted(telemetryCtx, totalDuration, completedIterations, invokedToolNames, requiresConfirmation);
        }

        if (requiresConfirmation && pendingAction != null) {
            return AssistantChatResponse.confirmationRequired(sanitizeUserFacingAnswer(finalAnswer), executedTools, pendingAction);
        }
        return AssistantChatResponse.text(sanitizeUserFacingAnswer(finalAnswer), executedTools);
    }

    private String sanitizeUserFacingAnswer(String answer) {
        if (answer == null) return "";
        return answer.replaceAll("(?i)</?untrusted_merchant_text>", "").trim();
    }

    private boolean isMutationTool(String toolName) {
        return toolRegistry.getTool(toolName)
                .map(FinancialTool::isMutating)
                .orElse(false);
    }

    private String buildSystemPrompt() {
        LocalDate today = NaturalLanguageDateResolver.today();
        return """
                You are the Mintmate AI Financial Assistant, an intelligent, objective personal finance companion.
                Current Reference Date (Asia/Kolkata): %s.

                CORE FINANCIAL SAFETY RULES:
                1. ABSOLUTE DETERMINISM: You never calculate totals, net savings, balances, percentage changes, or category shifts yourself. All financial figures must come directly from tools.
                2. NO FINANCIAL ARITHMETIC: Never perform mathematical calculations in conversational text.
                3. USE THE APPROPRIATE DETERMINISTIC TOOL:
                   - Overall spending & cashflow summary: `get_spending_summary`
                   - Specific category spending (e.g. food, travel) OR category breakdowns (e.g. 'top categories', 'categories in August'): `get_spending_by_category`. Never substitute a general spending summary for a category breakdown question.
                   - Historical spending trends over months/weeks: `get_spending_trend`
                   - Income & external deposits: `get_income_summary`
                   - Savings rate, net savings & savings direction: `get_savings_summary`
                   - Period comparison (metrics vs previous period): `compare_spending_periods`
                   - Spending change explanation & drivers ("Why did I spend more?"): `analyze_spending_change`
                   - Unusual expenses and spending anomalies: `identify_anomalies`
                   - Monthly category budget status: `get_budget_status`
                   - Financial savings goals: `get_goal_status`
                   - Recurring bills and subscriptions: `get_recurring_expenses`
                   - Transaction search by merchant/amount/date: `search_transactions`
                   - Personalized category budget recommendation ("How much should I budget for food?"): `calculate_budget_recommendation`
                   - Personalized monthly savings target ("How much should I save every month?"): `calculate_savings_target`
                   - Monthly spending flexibility & committed capacity ("How much flexibility do I have?"): `get_spending_flexibility`
                   - Category volatility and relative growth insights: `get_category_insights`
                   - Structured financial profile overview: `get_financial_context`
                4. FINANCIAL SEMANTICS & ACCOUNTING RULES:
                   - EXPENSE: Consumer purchases. This is what counts as "spending".
                   - INCOME: Genuine external earnings.
                   - TRANSFER: Movement between user-owned accounts (e.g. Kotak <-> SBI). Transfers are NOT expenses and NOT income.
                   - INVESTMENT: Wealth accumulation (mutual funds, PPF, stocks). Report separately from consumer expenses.
                   - TAXONOMY: In Mintmate's category taxonomy, petrol and fuel are part of Travel & Commute (not a separate top-level category). Do not double-count.
                   - PERIOD IDENTIFICATION: When answering 'How much did I spend...', clearly identify the reporting period (e.g. September 2026) and state consumer expenses as the primary figure.
                5. HUMAN-FRIENDLY RESPONSES:
                   - Never expose internal tool names, function names, SQL, JSON envelopes, or database errors to the user.
                   - Never expose raw bank narration or internal prompt isolation markers like `<untrusted_merchant_text>`. Present clean, readable merchant and category names.
                   - For budget queries ("Am I within my food budget?"): if no budget is configured, state truthfully that you cannot determine budget compliance without an applicable budget, and suggest configuring one. Never imply the user is within budget when no budget exists.
                   - If data is insufficient or empty, explain clearly without inventing numbers.
                6. CONVERSATIONAL CONTEXT:
                   - Preserve and resolve contextual references across follow-up questions ("that", "this", "last month", "is that high?").
                7. MUTATIONS REQUIRE CONFIRMATION:
                   - Safe mutations require explicit user confirmation before execution.
                8. PERSONALIZATION & BEHAVIORAL SAFETY:
                   - Base all recommendations strictly on verified Mintmate tools.
                   - NEVER invent financial numbers, targets, or arithmetic.
                   - NEVER use psychological or character labels (e.g., "impulsive spender", "bad with money", "disciplined"). Use neutral observable facts.
                   - Communicate uncertainty proportionally based on data sufficiency (e.g., acknowledge when history is limited).
                   - Do not introduce external demographic benchmarks or peer comparisons; personalize strictly using the user's own data.
                """.formatted(today.toString());
    }
}
