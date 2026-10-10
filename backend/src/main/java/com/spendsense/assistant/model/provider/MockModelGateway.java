package com.spendsense.assistant.model.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.model.*;
import com.spendsense.assistant.tool.ToolDefinition;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    private final ObjectMapper objectMapper;

    @Autowired
    public MockModelGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
    }

    public MockModelGateway() {
        this(createDefaultObjectMapper());
    }

    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

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
            return synthesizeFromToolResults(messages);
        }

        // 2. If the user asks a question, determine which tool to invoke
        String userPrompt = lastMessage.content() != null ? lastMessage.content().toLowerCase(Locale.ROOT) : "";

        // Check for conversational follow-ups ("what about last month?", "is that high?", "what should I spend instead?")
        String previousCategory = extractPreviousCategory(messages);
        if (!userPrompt.contains("compare") && (userPrompt.contains("what about last month") || userPrompt.contains("how about last month") || userPrompt.startsWith("and last month"))) {
            NaturalLanguageDateResolver.DateRange lastMonthRange = NaturalLanguageDateResolver.resolveRange("last month");
            if (previousCategory != null) {
                return AssistantResponse.ofToolCalls(List.of(
                        new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_by_category", Map.of(
                                "startDate", lastMonthRange.startDate().toString(),
                                "endDate", lastMonthRange.endDate().toString(),
                                "category", previousCategory
                        ))
                ));
            } else {
                return AssistantResponse.ofToolCalls(List.of(
                        new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_summary", Map.of(
                                "startDate", lastMonthRange.startDate().toString(),
                                "endDate", lastMonthRange.endDate().toString()
                        ))
                ));
            }
        }

        if (userPrompt.contains("is that high") || userPrompt.contains("is that normal") || userPrompt.contains("is that a lot")) {
            if (previousCategory != null) {
                String ym = YearMonth.from(NaturalLanguageDateResolver.today()).toString();
                return AssistantResponse.ofToolCalls(List.of(
                        new ToolCallRequest(UUID.randomUUID().toString(), "get_budget_status", Map.of(
                                "yearMonth", ym,
                                "category", previousCategory
                        ))
                ));
            } else {
                NaturalLanguageDateResolver.DateRange range = NaturalLanguageDateResolver.resolveRange("this month");
                return AssistantResponse.ofToolCalls(List.of(
                        new ToolCallRequest(UUID.randomUUID().toString(), "compare_spending_periods", Map.of(
                                "startDate", range.startDate().toString(),
                                "endDate", range.endDate().toString()
                        ))
                ));
            }
        }

        if (userPrompt.contains("what should i spend instead") || userPrompt.contains("how much should i spend")) {
            String ym = YearMonth.from(NaturalLanguageDateResolver.today()).toString();
            Map<String, Object> args = new HashMap<>();
            args.put("yearMonth", ym);
            if (previousCategory != null) {
                args.put("category", previousCategory);
            }
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_budget_status", args)
            ));
        }

        // Phase 8C: Action Proposals (Mutations require explicit user confirmation)
        if (containsAny(userPrompt, "set my food budget", "set food budget", "update food budget", "update my food budget",
                "set my travel budget", "set travel budget", "set shopping budget", "create a new budget for food")) {
            String cat = extractCategory(userPrompt, "FOOD");
            java.math.BigDecimal amount = extractAmount(userPrompt, new java.math.BigDecimal("8500.00"));
            String ym = YearMonth.from(NaturalLanguageDateResolver.today()).toString();
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "create_or_update_budget", Map.of(
                            "category", cat,
                            "limitAmount", amount,
                            "yearMonth", ym
                    ))
            ));
        }

        if (containsAny(userPrompt, "create a savings goal", "create a goal", "set a goal")) {
            java.math.BigDecimal amount = extractAmount(userPrompt, new java.math.BigDecimal("50000.00"));
            java.time.LocalDate targetDate = NaturalLanguageDateResolver.today().plusMonths(6);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "create_or_update_goal", Map.of(
                            "name", userPrompt.contains("vacation") ? "Vacation" : "Emergency Fund",
                            "targetAmount", amount,
                            "targetDate", targetDate.toString()
                    ))
            ));
        }

        if (containsAny(userPrompt, "recategorize transaction", "change category of transaction")) {
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "update_transaction_category", Map.of(
                            "transactionId", UUID.randomUUID().toString(),
                            "category", "SHOPPING"
                    ))
            ));
        }

        // Phase 8D: Proactive Insights
        if (containsAny(userPrompt, "proactive", "alerts", "financial warnings", "notification", "notifications", "spending alerts", "urgent budget warnings")) {
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_proactive_insights", Map.of())
            ));
        }

        // Phase 8B: Check for budget recommendations ("how much should I budget for food?", "realistic food budget", "recommend a budget", "what spending target")
        if (((userPrompt.contains("recommend") || userPrompt.contains("realistic")) && userPrompt.contains("budget"))
                || containsAny(userPrompt, "should i budget", "recommend a budget", "budget recommendation", "budget should i set",
                "realistic budget", "realistic food budget", "spending target would make sense", "what spending target",
                "spending target should i set", "how much to budget", "what should i budget", "budget for food", "budget for travel",
                "budget for shopping", "spending targets look like", "realistic monthly budget")) {
            String cat = extractCategory(userPrompt, previousCategory != null ? previousCategory : "FOOD");
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "calculate_budget_recommendation", Map.of(
                            "category", cat,
                            "lookbackMonths", 6
                    ))
            ));
        }

        // Phase 8B: Check for savings target recommendations ("how much should I save every month", "realistic monthly savings target")
        if (!userPrompt.contains("goal") && containsAny(userPrompt, "should i save", "savings target for me", "recommend a savings target",
                "realistic monthly savings target", "realistic savings target", "what should my savings target be",
                "how much can i save", "how much to save every month", "save each month", "save every month", "save monthly",
                "emergency fund target", "target be", "savings target")) {
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "calculate_savings_target", Map.of("lookbackMonths", 6))
            ));
        }

        // Phase 8B: Check for spending flexibility ("how much flexibility", "income is committed", "discretionary capacity")
        if (containsAny(userPrompt, "flexibility", "how much of my income is already committed", "income is already committed",
                "income is committed", "discretionary capacity", "flexibility do i actually have", "flexibility each month",
                "flexibility going", "where is most of my financial flexibility", "cushion", "spending room", "discretionary spending room", "financial cushion")) {
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_flexibility", Map.of("lookbackMonths", 6))
            ));
        }

        // Phase 8B: Check for category insights ("most volatile", "keep an eye on", "category insights", "consuming most of my discretionary")
        if (containsAny(userPrompt, "keep an eye on", "most volatile", "category insights", "consuming most of my",
                "taking most of my income", "which categories have increased the most", "consistently above my historical average",
                "greatest volatility", "which categories are taking most", "above historical average", "volatility", "monitor closely", "monitor")) {
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_category_insights", Map.of("lookbackMonths", 6))
            ));
        }

        // Phase 8B: Check for financial profile / context
        if (containsAny(userPrompt, "financial profile", "financial context", "overview of my financial profile",
                "tell us about this user", "understand my financial situation", "overview of my financial situation",
                "structured profile", "financial situation", "my financial situation")) {
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_financial_context", Map.of(
                            "lookbackMonths", 6,
                            "section", "ALL"
                    ))
            ));
        }

        // Check for recurring expenses / subscriptions
        if (containsAny(userPrompt, "recurring", "subscription", "subscriptions", "monthly bills", "repeating")) {
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_recurring_expenses", Map.of("lookbackMonths", 6))
            ));
        }

        // Check for anomalies & unusual spending
        if (containsAny(userPrompt, "unusual", "anomaly", "anomalies", "spike", "unexpected expense")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "identify_anomalies", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString()
                    ))
            ));
        }

        // Check for goals
        if (containsAny(userPrompt, "goal", "goals", "savings goal", "on track")) {
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_goal_status", Map.of())
            ));
        }

        // Check for spending change explanation / drivers ("why did I spend more", "what changed", "why did that happen")
        if (containsAny(userPrompt, "why did i spend more", "why did my spending increase", "why did spending increase", "why did i spend less",
                "why did spending decrease", "what changed", "spending drivers", "primary drivers", "why is my spending",
                "why did that happen", "drove the spending change", "drivers of my spending")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "analyze_spending_change", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString()
                    ))
            ));
        }

        // Check for period comparison ("did I spend more than last month", "compare to last month", "did my expenses increase")
        if (containsAny(userPrompt, "compare", "more this month", "less this month", "than last month", "comparison",
                "did i spend more", "did i spend less", "expenses increase", "expenses decrease",
                "did my expenses increase", "did my expenses decrease", "more than usual", "more than i normally do", "more than normal")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "compare_spending_periods", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString()
                    ))
            ));
        }

        // Check for spending trends ("spending trend", "how has my spending changed", "growing fastest", "evolve")
        if (containsAny(userPrompt, "trend", "spending trend", "how has my spending changed", "over time", "every month", "growing fastest", "evolve", "change over the last")) {
            String natural = "last 6 months";
            if (userPrompt.contains("3 month") || userPrompt.contains("last 3")) natural = "last 3 months";
            else if (userPrompt.contains("12 month") || userPrompt.contains("year") || userPrompt.contains("last year") || userPrompt.contains("past year")) natural = "last 12 months";
            else if (userPrompt.contains("30 days") || userPrompt.contains("past 30")) natural = "last 30 days";

            Map<String, Object> args = new HashMap<>();
            args.put("naturalPeriod", natural);
            if (userPrompt.contains("30 days") || userPrompt.contains("daily")) {
                args.put("period", "DAILY");
            } else if (userPrompt.contains("weekly")) {
                args.put("period", "WEEKLY");
            } else if (userPrompt.contains("monthly")) {
                args.put("period", "MONTHLY");
            }

            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_trend", args)
            ));
        }

        // Check for income summary ("how much did I earn", "income", "salary", "earnings")
        if (containsAny(userPrompt, "income", "earn", "salary", "earned", "earnings", "how much did i make")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_income_summary", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString()
                    ))
            ));
        }

        // Check for savings summary ("savings rate", "how much did I save", "net savings")
        if (containsAny(userPrompt, "savings rate", "how much did i save", "savings summary", "net savings", "am i saving", "save this month", "saved last month")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_savings_summary", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString()
                    ))
            ));
        }

        // Check for budget status
        if (containsAny(userPrompt, "budget", "spending limit")) {
            String ym = YearMonth.from(NaturalLanguageDateResolver.today()).toString();
            Map<String, Object> args = new HashMap<>();
            args.put("yearMonth", ym);
            if (containsAny(userPrompt, "food")) args.put("category", "FOOD");
            if (containsAny(userPrompt, "travel")) args.put("category", "TRAVEL");
            if (containsAny(userPrompt, "shopping")) args.put("category", "SHOPPING");
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_budget_status", args)
            ));
        }

        // Check for transaction search ("find transactions", "search transactions")
        if (containsAny(userPrompt, "find transaction", "find transactions", "search transaction", "search transactions", "find my transactions")) {
            Map<String, Object> args = new HashMap<>();
            if (userPrompt.contains("amazon")) args.put("search", "Amazon");
            if (userPrompt.contains("5000")) args.put("minAmount", new java.math.BigDecimal("5000.00"));
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "search_transactions", args)
            ));
        }

        // Check for category spending inquiries
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

        if (containsAny(userPrompt, "bills", "utility", "electricity", "broadband", "recharge")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_by_category", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString(),
                            "category", "BILLS"
                    ))
            ));
        }

        if (containsAny(userPrompt, "entertainment", "movie", "cinema", "theatre")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_by_category", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString(),
                            "category", "ENTERTAINMENT"
                    ))
            ));
        }

        if (containsAny(userPrompt, "health", "hospital", "pharmacy", "medicine", "doctor")) {
            NaturalLanguageDateResolver.DateRange range = extractDateRange(userPrompt, messages);
            return AssistantResponse.ofToolCalls(List.of(
                    new ToolCallRequest(UUID.randomUUID().toString(), "get_spending_by_category", Map.of(
                            "startDate", range.startDate().toString(),
                            "endDate", range.endDate().toString(),
                            "category", "HEALTH"
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
        if (containsAny(userPrompt, "transaction", "transferred", "over ", "under ", "highest", "recent", "largest expense", "biggest expense")) {
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

        // Check for general spending summary
        if (containsAny(userPrompt, "spend", "spent", "spending", "how much did i spend", "summary", "where did most of my money go", "cash flow")) {
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

    private AssistantResponse synthesizeFromToolResults(List<AssistantMessage> messages) {
        List<String> summaries = new ArrayList<>();
        boolean hasMutationProposal = false;

        for (int i = messages.size() - 1; i >= 0; i--) {
            AssistantMessage msg = messages.get(i);
            if (msg.role() != AssistantMessageRole.TOOL) {
                break;
            }
            try {
                Map<String, Object> envelope = objectMapper.readValue(msg.content(), new TypeReference<>() {});
                String summaryText = (String) envelope.get("summaryText");
                if (summaryText != null && !summaryText.isBlank()) {
                    String toolName = msg.toolName();
                    if (toolName != null && (toolName.startsWith("create_or_update") || toolName.startsWith("update_transaction"))) {
                        hasMutationProposal = true;
                    }
                    summaries.add(0, summaryText);
                } else if (envelope.containsKey("error")) {
                    summaries.add(0, (String) envelope.get("error"));
                }
            } catch (Exception e) {
                log.debug("Could not parse tool summary: {}", e.getMessage());
            }
        }

        if (!summaries.isEmpty()) {
            String combined = String.join(" ", summaries);
            if (hasMutationProposal) {
                return AssistantResponse.text("I can " + combined + ". Would you like me to proceed? Please confirm to apply this change.");
            }
            return AssistantResponse.text(combined);
        }

        return AssistantResponse.text("Here is the verified data from your Mintmate records: " + messages.getLast().content());
    }

    private java.math.BigDecimal extractAmount(String text, java.math.BigDecimal defaultAmount) {
        if (text == null) return defaultAmount;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:₹|rs\\.?\\s*)?([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)").matcher(text);
        while (m.find()) {
            try {
                String clean = m.group(1).replace(",", "");
                java.math.BigDecimal val = new java.math.BigDecimal(clean);
                if (val.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    return val;
                }
            } catch (Exception ignored) {}
        }
        return defaultAmount;
    }

    private String extractPreviousCategory(List<AssistantMessage> messages) {
        if (messages.size() < 2) return null;
        for (int i = messages.size() - 2; i >= 0; i--) {
            String c = messages.get(i).content();
            if (c == null) continue;
            String lower = c.toLowerCase(Locale.ROOT);
            if (lower.contains("food") || lower.contains("dining")) return "FOOD";
            if (lower.contains("travel") || lower.contains("flight") || lower.contains("uber")) return "TRAVEL";
            if (lower.contains("shopping")) return "SHOPPING";
            if (lower.contains("bill")) return "BILLS";
            if (lower.contains("entertainment")) return "ENTERTAINMENT";
            if (lower.contains("health")) return "HEALTH";
        }
        return null;
    }

    private String extractCategory(String text, String defaultCategory) {
        if (text == null) return defaultCategory;
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "food", "dining", "restaurant", "swiggy", "zomato", "groceries", "grocery")) return "FOOD";
        if (containsAny(lower, "travel", "flight", "uber", "ola", "cab", "transport", "fuel")) return "TRAVEL";
        if (containsAny(lower, "shopping", "amazon", "flipkart", "clothes", "apparel")) return "SHOPPING";
        if (containsAny(lower, "bill", "utility", "electricity", "broadband", "recharge")) return "BILLS";
        if (containsAny(lower, "entertainment", "movie", "cinema", "theatre", "netflix", "games")) return "ENTERTAINMENT";
        if (containsAny(lower, "health", "hospital", "pharmacy", "medicine", "doctor")) return "HEALTH";
        if (containsAny(lower, "education", "course", "tuition", "school", "books")) return "EDUCATION";
        if (containsAny(lower, "personal", "salon", "grooming", "gym")) return "PERSONAL";
        return defaultCategory;
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
