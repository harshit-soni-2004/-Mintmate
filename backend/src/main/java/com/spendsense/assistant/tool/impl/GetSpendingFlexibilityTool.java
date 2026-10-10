package com.spendsense.assistant.tool.impl;

import com.spendsense.assistant.context.FinancialContextService;
import com.spendsense.assistant.dto.SpendingFlexibilityResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

@Component
public class GetSpendingFlexibilityTool implements FinancialTool<GetSpendingFlexibilityTool.Input, SpendingFlexibilityResponse> {

    public static final String NAME = "get_spending_flexibility";

    private final FinancialContextService contextService;

    public GetSpendingFlexibilityTool(FinancialContextService contextService) {
        this.contextService = contextService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Determine monthly spending flexibility: calculates how much of monthly income is committed to fixed recurring obligations and baseline expenses vs. remaining available discretionary capacity.";
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
                        "lookbackMonths", Map.of("type", "integer", "description", "Months of history to analyze (default: 6)")
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<SpendingFlexibilityResponse> execute(Input input, SpendSenseUserPrincipal user) {
        int lookback = input != null && input.lookbackMonths() != null && input.lookbackMonths() > 0
                ? Math.min(input.lookbackMonths(), 24)
                : 6;

        SpendingFlexibilityResponse response = contextService.getSpendingFlexibility(user, lookback);
        LocalDate today = NaturalLanguageDateResolver.today();
        LocalDate fromDate = today.minusMonths(lookback);

        String summaryText = String.format(
                "Monthly financial flexibility: From ₹%.2f average monthly income, ₹%.2f (%.1f%%) is committed to recurring expenses and ₹%.2f to baseline living. Estimated discretionary capacity is ₹%.2f/month. (Data sufficiency: %s across %d months).",
                response.monthlyIncome(),
                response.committedMonthlyExpenses(),
                response.committedPercentage(),
                response.typicalBaselineExpenses(),
                response.availableDiscretionaryCapacity(),
                response.dataSufficiency(),
                lookback
        );

        ToolResponseEnvelope<SpendingFlexibilityResponse> envelope = ToolResponseEnvelope.of(
                response,
                fromDate,
                today,
                "SPENDING_FLEXIBILITY",
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input(
            Integer lookbackMonths
    ) {}
}
