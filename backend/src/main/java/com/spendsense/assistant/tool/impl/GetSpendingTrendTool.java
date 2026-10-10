package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.analytics.AggregationPeriod;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.dto.SpendingTrendPoint;
import com.spendsense.analytics.dto.SpendingTrendResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GetSpendingTrendTool implements FinancialTool<GetSpendingTrendTool.Input, SpendingTrendResponse> {

    public static final String NAME = "get_spending_trend";

    private final AnalyticsService analyticsService;

    public GetSpendingTrendTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve aggregated spending and income trends (daily, weekly, or monthly) over a specified period (e.g. last 30 days, last 3 months, last 6 months, last 12 months) without raw transaction noise.";
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
                        "startDate", Map.of("type", "string", "format", "date", "description", "Optional start date YYYY-MM-DD"),
                        "endDate", Map.of("type", "string", "format", "date", "description", "Optional end date YYYY-MM-DD"),
                        "period", Map.of("type", "string", "enum", List.of("DAILY", "WEEKLY", "MONTHLY"), "description", "Aggregation resolution"),
                        "naturalPeriod", Map.of("type", "string", "description", "E.g. 'last 30 days', 'last 3 months', 'last 6 months', 'last 12 months'")
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<SpendingTrendResponse> execute(Input input, SpendSenseUserPrincipal user) {
        LocalDate start = input.startDate();
        LocalDate end = input.endDate();

        if (start == null || end == null) {
            String expr = input.naturalPeriod() != null && !input.naturalPeriod().isBlank()
                    ? input.naturalPeriod()
                    : "last 6 months";
            NaturalLanguageDateResolver.DateRange range = resolveTrendRange(expr);
            start = range.startDate();
            end = range.endDate();
        }

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        long daysBetween = ChronoUnit.DAYS.between(start, end);
        AggregationPeriod agg = input.period();
        if (agg == null) {
            if (daysBetween > 90) {
                agg = AggregationPeriod.MONTHLY;
            } else if (daysBetween > 30) {
                agg = AggregationPeriod.WEEKLY;
            } else {
                agg = AggregationPeriod.DAILY;
            }
        }

        SpendingTrendResponse trend = analyticsService.getTrends(agg, start, end);
        List<SpendingTrendPoint> pts = trend.points() != null ? trend.points() : Collections.emptyList();

        BigDecimal totalExpense = pts.stream()
                .map(SpendingTrendPoint::totalExpense)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgExpense = !pts.isEmpty()
                ? totalExpense.divide(BigDecimal.valueOf(pts.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String summaryText = String.format(
                "Spending trend from %s to %s aggregated %s across %d intervals: Total expense was ₹%.2f (average ₹%.2f per %s).",
                trend.fromDate(),
                trend.toDate(),
                agg.name().toLowerCase(),
                pts.size(),
                totalExpense,
                avgExpense,
                agg == AggregationPeriod.MONTHLY ? "month" : (agg == AggregationPeriod.WEEKLY ? "week" : "day")
        );

        ToolResponseEnvelope<SpendingTrendResponse> envelope = ToolResponseEnvelope.of(
                trend,
                start,
                end,
                agg.name(),
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    private NaturalLanguageDateResolver.DateRange resolveTrendRange(String expr) {
        String lower = expr.toLowerCase();
        LocalDate now = NaturalLanguageDateResolver.today();
        if (lower.contains("6 month") || lower.contains("last 6")) {
            return new NaturalLanguageDateResolver.DateRange(now.minusMonths(6).withDayOfMonth(1), now);
        }
        if (lower.contains("3 month") || lower.contains("last 3")) {
            return new NaturalLanguageDateResolver.DateRange(now.minusMonths(3).withDayOfMonth(1), now);
        }
        if (lower.contains("12 month") || lower.contains("year") || lower.contains("past year")) {
            return new NaturalLanguageDateResolver.DateRange(now.minusMonths(12).withDayOfMonth(1), now);
        }
        return NaturalLanguageDateResolver.resolveRange(expr);
    }

    public record Input(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate startDate,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate endDate,

            AggregationPeriod period,

            String naturalPeriod
    ) {}
}
