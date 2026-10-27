package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.TrendDirection;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.assistant.dto.IncomeSummaryResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GetIncomeSummaryTool implements FinancialTool<GetIncomeSummaryTool.Input, IncomeSummaryResponse> {

    public static final String NAME = "get_income_summary";

    private final AnalyticsService analyticsService;
    private final TransactionRepository transactionRepository;

    public GetIncomeSummaryTool(AnalyticsService analyticsService, TransactionRepository transactionRepository) {
        this.analyticsService = analyticsService;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve verified earnings and external income summary (total income, number of income deposits, average deposit size, and period-over-period income growth). Internal transfers are strictly excluded.";
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
    public ToolExecutionResult<IncomeSummaryResponse> execute(Input input, SpendSenseUserPrincipal user) {
        LocalDate start = input.startDate() != null ? input.startDate() : NaturalLanguageDateResolver.today().withDayOfMonth(1);
        LocalDate end = input.endDate() != null ? input.endDate() : NaturalLanguageDateResolver.today();

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        SpendingSummaryResponse summary = analyticsService.getSummary(start, end);
        PeriodComparisonResponse comparison = analyticsService.getComparison(start, end);

        long incomeTxCount = transactionRepository.countByUserIdAndTypeAndDateBetween(
                user.getUserId(), TransactionType.INCOME, start, end);

        BigDecimal avgIncomeTx = (incomeTxCount > 0 && summary.totalIncome().compareTo(BigDecimal.ZERO) > 0)
                ? summary.totalIncome().divide(BigDecimal.valueOf(incomeTxCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal prevIncome = comparison.income() != null ? comparison.income().previousAmount() : BigDecimal.ZERO;
        BigDecimal change = comparison.income() != null ? comparison.income().changeAmount() : BigDecimal.ZERO;
        BigDecimal pctChange = (comparison.income() != null && comparison.income().percentageChange() != null)
                ? comparison.income().percentageChange() : BigDecimal.ZERO;
        TrendDirection direction = comparison.income() != null ? comparison.income().direction() : TrendDirection.UNCHANGED;

        IncomeSummaryResponse response = new IncomeSummaryResponse(
                summary.totalIncome(),
                incomeTxCount,
                avgIncomeTx,
                prevIncome,
                change,
                pctChange,
                direction,
                start,
                end
        );

        String summaryText = String.format(
                "From %s to %s, total verified income was ₹%.2f across %d deposit(s) (average deposit: ₹%.2f). Compared to prior period (₹%.2f), income changed by %.1f%% (%s).",
                start,
                end,
                summary.totalIncome(),
                incomeTxCount,
                avgIncomeTx,
                prevIncome,
                pctChange,
                direction
        );

        ToolResponseEnvelope<IncomeSummaryResponse> envelope = ToolResponseEnvelope.of(
                response,
                start,
                end,
                "INCOME",
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
