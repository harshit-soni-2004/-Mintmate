package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
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
public class GetSpendingSummaryTool implements FinancialTool<GetSpendingSummaryTool.Input, SpendingSummaryResponse> {

    public static final String NAME = "get_spending_summary";

    private final AnalyticsService analyticsService;

    public GetSpendingSummaryTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve verified aggregate cash flow summary (total income, consumer expenses, net savings, savings rate, transfers, investment contributions, cash withdrawals) for an explicit date range.";
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
                        "startDate", Map.of("type", "string", "format", "date", "description", "Start date YYYY-MM-DD (inclusive)"),
                        "endDate", Map.of("type", "string", "format", "date", "description", "End date YYYY-MM-DD (inclusive)")
                ),
                "required", List.of("startDate", "endDate")
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<SpendingSummaryResponse> execute(Input input, SpendSenseUserPrincipal user) {
        LocalDate start = input.startDate() != null ? input.startDate() : NaturalLanguageDateResolver.today().withDayOfMonth(1);
        LocalDate end = input.endDate() != null ? input.endDate() : NaturalLanguageDateResolver.today();

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        SpendingSummaryResponse summary = analyticsService.getSummary(start, end);

        String summaryText = String.format(
                "From %s to %s, your total consumer spending was ₹%.2f across %d transactions. In this period, total verified income was ₹%.2f, consumer expenses were ₹%.2f, resulting in net savings of ₹%.2f (savings rate: %.1f%%). Internal transfers out were ₹%.2f, and investment contributions were ₹%.2f (both tracked separately and excluded from consumer spending).",
                summary.fromDate(),
                summary.toDate(),
                summary.totalExpenses(),
                summary.transactionCount(),
                summary.totalIncome(),
                summary.totalExpenses(),
                summary.netSavings(),
                summary.savingsRate(),
                summary.totalTransfers(),
                summary.investmentContributions()
        );

        ToolResponseEnvelope<SpendingSummaryResponse> envelope = ToolResponseEnvelope.of(
                summary,
                summary.fromDate(),
                summary.toDate(),
                "ALL_ACCOUNTS",
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
