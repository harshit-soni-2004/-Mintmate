package com.spendsense.assistant.tool.impl;

import com.spendsense.assistant.context.FinancialContextService;
import com.spendsense.assistant.context.model.FinancialProfile;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GetFinancialContextTool implements FinancialTool<GetFinancialContextTool.Input, FinancialProfile> {

    public static final String NAME = "get_financial_context";

    private final FinancialContextService contextService;

    public GetFinancialContextTool(FinancialContextService contextService) {
        this.contextService = contextService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve the user's structured deterministic financial profile (income stability, spending baseline, category averages, recurring commitments, and data sufficiency) across historical months.";
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
                        "lookbackMonths", Map.of("type", "integer", "description", "Historical window in months to profile (default: 6)"),
                        "section", Map.of("type", "string", "enum", List.of("ALL", "INCOME", "SPENDING", "SAVINGS", "CATEGORIES", "RECURRING", "FLEXIBILITY"), "description", "Targeted profile slice")
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<FinancialProfile> execute(Input input, SpendSenseUserPrincipal user) {
        int lookback = input != null && input.lookbackMonths() != null && input.lookbackMonths() > 0
                ? Math.min(input.lookbackMonths(), 24)
                : 6;

        FinancialProfile profile = contextService.computeFinancialProfile(user, lookback);
        LocalDate today = NaturalLanguageDateResolver.today();
        LocalDate fromDate = today.minusMonths(lookback);

        String summaryText = String.format(
                "Financial profile (%d-month lookback, sufficiency: %s): Average monthly income is ₹%.2f, average expenses ₹%.2f, committed recurring expenses ₹%.2f, average monthly savings ₹%.2f (rate: %.1f%%). Discretionary flexibility is estimated at ₹%.2f/month.",
                lookback,
                profile.dataSufficiency().level(),
                profile.income().averageMonthlyIncome(),
                profile.spending().averageMonthlyExpenses(),
                profile.recurring().totalMonthlyCommitted(),
                profile.savings().averageMonthlySavings(),
                profile.savings().averageSavingsRate(),
                profile.flexibility().availableDiscretionaryCapacity()
        );

        ToolResponseEnvelope<FinancialProfile> envelope = ToolResponseEnvelope.of(
                profile,
                fromDate,
                today,
                "FINANCIAL_PROFILE",
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input(
            Integer lookbackMonths,
            String section
    ) {}
}
