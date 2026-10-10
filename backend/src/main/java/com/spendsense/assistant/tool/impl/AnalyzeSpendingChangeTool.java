package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.TrendDirection;
import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.CategoryComparison;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.assistant.dto.SpendingChangeAnalysisResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class AnalyzeSpendingChangeTool implements FinancialTool<AnalyzeSpendingChangeTool.Input, SpendingChangeAnalysisResponse> {

    public static final String NAME = "analyze_spending_change";

    private final AnalyticsService analyticsService;

    public AnalyzeSpendingChangeTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Deterministic analysis explaining why spending increased or decreased compared to the previous period, identifying the specific category drivers responsible for the shift.";
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
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<SpendingChangeAnalysisResponse> execute(Input input, SpendSenseUserPrincipal user) {
        LocalDate start = input.startDate() != null ? input.startDate() : NaturalLanguageDateResolver.today().withDayOfMonth(1);
        LocalDate end = input.endDate() != null ? input.endDate() : NaturalLanguageDateResolver.today();

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        PeriodComparisonResponse comparison = analyticsService.getComparison(start, end);
        CategoryBreakdownResponse breakdown = analyticsService.getCategoryBreakdown(start, end);

        BigDecimal curExpenses = comparison.expenses() != null ? comparison.expenses().currentAmount() : BigDecimal.ZERO;
        BigDecimal prevExpenses = comparison.expenses() != null ? comparison.expenses().previousAmount() : BigDecimal.ZERO;
        BigDecimal absChange = comparison.expenses() != null ? comparison.expenses().changeAmount() : BigDecimal.ZERO;
        BigDecimal pctChange = comparison.expenses() != null ? comparison.expenses().percentageChange() : BigDecimal.ZERO;
        TrendDirection direction = comparison.expenses() != null ? comparison.expenses().direction() : TrendDirection.UNCHANGED;

        List<CategoryComparison> allChanges = new ArrayList<>();
        if (breakdown != null && breakdown.categories() != null) {
            for (CategoryAnalysis ca : breakdown.categories()) {
                allChanges.add(new CategoryComparison(
                        ca.category().getDisplayName(),
                        ca.amount(),
                        ca.previousAmount() != null ? ca.previousAmount() : BigDecimal.ZERO,
                        ca.changeAmount() != null ? ca.changeAmount() : BigDecimal.ZERO,
                        ca.percentageChange() != null ? ca.percentageChange() : BigDecimal.ZERO
                ));
            }
        }

        // Primary drivers: if spending increased, categories that increased most; if decreased, categories that decreased most
        List<CategoryComparison> primaryDrivers = allChanges.stream()
                .sorted((a, b) -> b.change().abs().compareTo(a.change().abs()))
                .limit(4)
                .toList();

        StringBuilder summaryBuilder = new StringBuilder();
        if (direction == TrendDirection.INCREASED) {
            summaryBuilder.append(String.format(
                    "Overall spending increased by ₹%.2f (+%.1f%%) from ₹%.2f to ₹%.2f compared to prior period. ",
                    absChange, pctChange, prevExpenses, curExpenses
            ));
        } else if (direction == TrendDirection.DECREASED) {
            summaryBuilder.append(String.format(
                    "Overall spending decreased by ₹%.2f (%.1f%%) from ₹%.2f to ₹%.2f compared to prior period. ",
                    absChange.abs(), pctChange, prevExpenses, curExpenses
            ));
        } else {
            summaryBuilder.append(String.format(
                    "Overall spending remained unchanged at ₹%.2f compared to prior period. ",
                    curExpenses
            ));
        }

        if (!primaryDrivers.isEmpty()) {
            summaryBuilder.append("Primary category drivers: ");
            summaryBuilder.append(String.join("; ", primaryDrivers.stream()
                    .map(d -> String.format("%s: ₹%.2f (was ₹%.2f, delta: %s₹%.2f, %s%.1f%%)",
                            d.category(), d.current(), d.previous(),
                            d.change().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "", d.change(),
                            d.changePercentage().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "", d.changePercentage()))
                    .toList()));
            summaryBuilder.append(".");
        }

        SpendingChangeAnalysisResponse response = new SpendingChangeAnalysisResponse(
                curExpenses,
                prevExpenses,
                absChange,
                pctChange,
                direction,
                primaryDrivers,
                allChanges,
                comparison.currentFrom(),
                comparison.currentTo(),
                comparison.previousFrom(),
                comparison.previousTo()
        );

        ToolResponseEnvelope<SpendingChangeAnalysisResponse> envelope = ToolResponseEnvelope.of(
                response,
                start,
                end,
                "SPENDING_CHANGE",
                summaryBuilder.toString(),
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
