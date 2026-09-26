package com.spendsense.assistant.model.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.model.*;
import com.spendsense.assistant.tool.ToolDefinition;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Deterministic model gateway for offline development, integration tests, and fallback execution.
 * Simulates intent extraction and tool call emission using deterministic keyword and date resolution,
 * guaranteeing zero hallucination and 100% test reproducibility.
 */
@Component
public class MockModelGateway implements ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(MockModelGateway.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public AssistantResponse generateResponse(AssistantConversationContext context, List<ToolDefinition> tools) {
        List<AssistantMessage> messages = context.messages();
        if (messages.isEmpty()) {
            return AssistantResponse.text("Hello! How can I help you with your finances today?");
        }

        AssistantMessage lastMessage = messages.getLast();

        // 1. If the last message is a TOOL execution result, synthesize the final grounded response
        if (lastMessage.role() == AssistantMessageRole.TOOL) {
            return synthesizeFromToolResult(lastMessage);
        }

        // 2. If the user asks a question, determine which tool to invoke
        String userPrompt = lastMessage.content() != null ? lastMessage.content().toLowerCase(Locale.ROOT) : "";

        // Check for budget status
        if (containsAny(userPrompt, "budget")) {
            String ym = YearMonth.from(NaturalLanguageDateResolver.today()).toString();
            Map<String, Object> args = new HashMap<>();
            args.put("yearMonth", ym);
            if (containsAny(userPrompt, "food")) {
                args.put("category", "FOOD");
            }
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_budget_status", args)
            ));
        }

        // Check for category spending inquiry
        if (containsAny(userPrompt, "food", "dining", "restaurant", "swiggy", "zomato")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_by_category", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString(),
                            "category", "FOOD"
                    ))
            ));
        }

        if (containsAny(userPrompt, "travel", "flight", "uber", "ola", "cab")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_by_category", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString(),
                            "category", "TRAVEL"
                    ))
            ));
        }

        if (containsAny(userPrompt, "shopping", "amazon", "flipkart")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_by_category", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString(),
                            "category", "SHOPPING"
                    ))
            ));
        }

        // Check for period comparison
        if (containsAny(userPrompt, "compare", "more this month", "less this month", "than last month")) {
            NaturalLanguageDateResolver.DateRange range = NaturalLanguageDateResolver.resolveRange("this month");
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "compare_spending_periods", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString()
                    ))
            ));
        }

        // Check for accounts
        if (containsAny(userPrompt, "account", "bank", "linked", "sbi", "kotak")) {
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_financial_accounts", Map.of("activeOnly", true))
            ));
        }

        // Check for transactions search
        if (containsAny(userPrompt, "transaction", "transferred", "over", "under", "highest", "recent", "swiggy", "netflix")) {
            Map<String, Object> args = new HashMap<>();
            if (userPrompt.contains("5000") || userPrompt.contains("5,000")) {
                args.put("minAmount", 5000);
            }
            if (userPrompt.contains("transfer")) {
                args.put("transactionType", "TRANSFER");
            }
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "search_transactions", args)
            ));
        }

        // Check for general spending summary (e.g. "how much did I spend", "summary", "income", "savings")
        if (containsAny(userPrompt, "spend", "spent", "spending", "income", "save", "savings", "earn")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_summary", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString()
                    ))
            ));
        }

        // Fallback default response
        return AssistantResponse.text("I can help you review your spending summary, category expenses, budgets, linked accounts, or transaction history. What would you like to explore?");
    }

    private AssistantResponse synthesizeFromToolResult(AssistantMessage toolMessage) {
        try {
            Map<String, Object> envelope = OBJECT_MAPPER.readValue(toolMessage.content(), new TypeReference<>() {});
            String summaryText = (String) envelope.get("summaryText");
            if (summaryText != null && !summaryText.isBlank()) {
                return AssistantResponse.text(summaryText);
            }
        } catch (Exception e) {
            log.debug("Could not parse tool summary: {}", e.getMessage());
        }

        return AssistantResponse.text("Here is the verified data from your SpendSense records: " + toolMessage.content());
    }

    private NaturalLanguageDateResolver.DateRange extractDateRange(String userPrompt, List<AssistantMessage> messages) {
        if (userPrompt.contains("last month") || userPrompt.contains("previous month")) {
            return NaturalLanguageDateResolver.resolveRange("last month");
        }
        if (userPrompt.contains("this month") || userPrompt.contains("current month")) {
            return NaturalLanguageDateResolver.resolveRange("this month");
        }
        if (userPrompt.contains("last 30 days")) {
            return NaturalLanguageDateResolver.resolveRange("last 30 days");
        }
        if (userPrompt.contains("this week")) {
            return NaturalLanguageDateResolver.resolveRange("this week");
        }
        if (userPrompt.contains("last week")) {
            return NaturalLanguageDateResolver.resolveRange("last week");
        }
        if (userPrompt.contains("this year")) {
            return NaturalLanguageDateResolver.resolveRange("this year");
        }
        if (userPrompt.contains("last year")) {
            return NaturalLanguageDateResolver.resolveRange("last year");
        }

        // Check if previous turn had "last month"
        if (messages.size() >= 3) {
            for (int i = messages.size() - 2; i >= 0; i--) {
                String prev = messages.get(i).content();
                if (prev != null && prev.toLowerCase(Locale.ROOT).contains("last month")) {
                    return NaturalLanguageDateResolver.resolveRange("last month");
                }
            }
        }

        // Default to current month
        return NaturalLanguageDateResolver.resolveRange("this month");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
