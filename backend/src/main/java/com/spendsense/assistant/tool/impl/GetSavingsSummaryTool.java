package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.TrendDirection;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.assistant.dto.SavingsSummaryResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GetSavingsSummaryTool implements FinancialTool<GetSavingsSummaryTool.Input, SavingsSummaryResponse> {

    public static final String NAME = "get_savings_summary";

    private final AnalyticsService analyticsService;

    public GetSavingsSummaryTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve verified savings analysis (net savings = income - consumer expenses, savings rate percentage, and whether savings increased or decreased vs previous period). Transfers are not counted as spending or savings.";
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
                        "endDate", Map.of("type", "string", "format", "date", "description", "End date YYYY-MM-DD")
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<SavingsSummaryResponse> execute(Input input, SpendSenseUserPrincipal user) {
        LocalDate start = input.startDate() != null ? input.startDate() : NaturalLanguageDateResolver.today().withDayOfMonth(1);
        LocalDate end = input.endDate() != null ? input.endDate() : NaturalLanguageDateResolver.today();

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        SpendingSummaryResponse summary = analyticsService.getSummary(start, end);
        PeriodComparisonResponse comparison = analyticsService.getComparison(start, end);

        BigDecimal netSavings = summary.netSavings();
        BigDecimal savingsRate = summary.savingsRate();

        String status;
        if (netSavings.compareTo(BigDecimal.ZERO) > 0) {
            status = "POSITIVE";
        } else if (netSavings.compareTo(BigDecimal.ZERO) < 0) {
            status = "NEGATIVE";
        } else {
            status = "BREAK_EVEN";
        }

        BigDecimal prevNetSavings = comparison.netSavings() != null ? comparison.netSavings().previousAmount() : BigDecimal.ZERO;
        BigDecimal prevSavingsRate = comparison.previousSavingsRate() != null ? comparison.previousSavingsRate() : BigDecimal.ZERO;
        BigDecimal savingsRateChange = comparison.savingsRateChange() != null ? comparison.savingsRateChange() : BigDecimal.ZERO;
        TrendDirection direction = comparison.netSavings() != null ? comparison.netSavings().direction() : TrendDirection.UNCHANGED;

        SavingsSummaryResponse response = new SavingsSummaryResponse(
                summary.totalIncome(),
                summary.totalExpenses(),
                netSavings,
                savingsRate,
                status,
                prevNetSavings,
                prevSavingsRate,
                savingsRateChange,
                direction,
                start,
                end
        );

        String summaryText = String.format(
                "From %s to %s, total income was ₹%.2f and consumer expenses were ₹%.2f, leaving net savings of ₹%.2f (savings rate: %.1f%%, %s). Compared to prior period (₹%.2f, %.1f%%), savings rate changed by %s%.1f percentage points.",
                start,
                end,
                summary.totalIncome(),
                summary.totalExpenses(),
                netSavings,
                savingsRate,
                status,
                prevNetSavings,
                prevSavingsRate,
                savingsRateChange.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "",
                savingsRateChange
        );

        ToolResponseEnvelope<SavingsSummaryResponse> envelope = ToolResponseEnvelope.of(
                response,
                start,
                end,
                "SAVINGS",
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate startDate,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate endDate
    ) {}
}
