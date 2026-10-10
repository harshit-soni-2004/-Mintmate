package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GetSpendingByCategoryTool implements FinancialTool<GetSpendingByCategoryTool.Input, CategoryBreakdownResponse> {

    public static final String NAME = "get_spending_by_category";

    private final AnalyticsService analyticsService;

    public GetSpendingByCategoryTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve breakdown of verified consumer expenses by category (FOOD, TRAVEL, SHOPPING, ENTERTAINMENT, BILLS, HEALTH, EDUCATION, PERSONAL, OTHER) for a date range.";
    }

    @Override
    public Class<Input> getInputType() {
        return Input.class;
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "startDate", Map.of("type", "string", "format", "date", "description", "Start date YYYY-MM-DD"),
                        "endDate", Map.of("type", "string", "format", "date", "description", "End date YYYY-MM-DD"),
                        "category", Map.of("type", "string", "enum", List.of("FOOD", "TRAVEL", "SHOPPING", "ENTERTAINMENT", "BILLS", "HEALTH", "EDUCATION", "PERSONAL", "OTHER"), "description", "Optional category filter")
                ),
                "required", List.of("startDate", "endDate")
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<CategoryBreakdownResponse> execute(Input input, SpendSenseUserPrincipal user) {
        LocalDate start = input.startDate() != null ? input.startDate() : NaturalLanguageDateResolver.today().withDayOfMonth(1);
        LocalDate end = input.endDate() != null ? input.endDate() : NaturalLanguageDateResolver.today();

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        CategoryBreakdownResponse breakdown = analyticsService.getCategoryBreakdown(start, end);

        String summaryText;
        if (input.category() != null) {
            Optional<CategoryAnalysis> matched = breakdown.categories().stream()
                    .filter(c -> c.category() == input.category())
                    .findFirst();

            if (matched.isPresent()) {
                CategoryAnalysis c = matched.get();
                summaryText = String.format("From %s to %s, spending on %s was ₹%.2f across %d transactions (%.1f%% of total expenses).",
                        start, end, c.category().getDisplayName(), c.amount(), c.transactionCount(), c.percentage());
            } else {
                summaryText = String.format("From %s to %s, there was no recorded spending for category %s.",
                        start, end, input.category().getDisplayName());
            }
        } else {
            summaryText = String.format("From %s to %s, total consumer expenses were ₹%.2f across %d categories.",
                    start, end, breakdown.totalExpenses(), breakdown.categories().size());
        }

        ToolResponseEnvelope<CategoryBreakdownResponse> envelope = ToolResponseEnvelope.of(
                breakdown,
                start,
                end,
                input.category() != null ? input.category().name() : "ALL_CATEGORIES",
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input(
            @NotNull(message = "startDate is required")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate startDate,

            @NotNull(message = "endDate is required")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate endDate,

            TransactionCategory category
    ) {}
}
