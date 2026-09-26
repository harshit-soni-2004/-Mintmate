package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CompareSpendingPeriodsTool implements FinancialTool<CompareSpendingPeriodsTool.Input, PeriodComparisonResponse> {

    public static final String NAME = "compare_spending_periods";

    private final AnalyticsService analyticsService;

    public CompareSpendingPeriodsTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Compare verified financial metrics (income, expenses, savings rate change) between a given date range and the preceding period of identical duration.";
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
                        "startDate", Map.of("type", "string", "format", "date", "description", "Current period start date YYYY-MM-DD"),
                        "endDate", Map.of("type", "string", "format", "date", "description", "Current period end date YYYY-MM-DD")
                ),
                "required", List.of("startDate", "endDate")
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<PeriodComparisonResponse> execute(Input input, SpendSenseUserPrincipal user) {
        LocalDate start = input.startDate() != null ? input.startDate() : NaturalLanguageDateResolver.today().withDayOfMonth(1);
        LocalDate end = input.endDate() != null ? input.endDate() : NaturalLanguageDateResolver.today();

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        PeriodComparisonResponse comparison = analyticsService.getComparison(start, end);

        String summaryText = String.format(
                "Comparison: From %s to %s vs prior period (%s to %s): Expenses changed by %.1f%% (%s), income changed by %.1f%% (%s), and savings rate changed by %.1f percentage points.",
                comparison.currentFrom(),
                comparison.currentTo(),
                comparison.previousFrom(),
                comparison.previousTo(),
                comparison.expenses().percentageChange(),
                comparison.expenses().direction(),
                comparison.income().percentageChange(),
                comparison.income().direction(),
                comparison.savingsRateChange()
        );

        ToolResponseEnvelope<PeriodComparisonResponse> envelope = ToolResponseEnvelope.of(
                comparison,
                start,
                end,
                "COMPARISON",
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
            LocalDate endDate
    ) {}
}
