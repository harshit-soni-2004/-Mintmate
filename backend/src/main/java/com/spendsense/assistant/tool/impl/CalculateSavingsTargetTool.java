package com.spendsense.assistant.tool.impl;

import com.spendsense.assistant.context.FinancialContextService;
import com.spendsense.assistant.dto.SavingsTargetRecommendationResponse;
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
public class CalculateSavingsTargetTool implements FinancialTool<CalculateSavingsTargetTool.Input, SavingsTargetRecommendationResponse> {

    public static final String NAME = "calculate_savings_target";

    private final FinancialContextService contextService;

    public CalculateSavingsTargetTool(FinancialContextService contextService) {
        this.contextService = contextService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Calculate a realistic, personalized monthly savings target and savings rate grounded in verified net cashflow, active savings goals, and fixed commitments.";
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
                        "lookbackMonths", Map.of("type", "integer", "description", "Historical window in months to analyze (default: 6)")
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<SavingsTargetRecommendationResponse> execute(Input input, SpendSenseUserPrincipal user) {
        int lookback = input != null && input.lookbackMonths() != null && input.lookbackMonths() > 0
                ? Math.min(input.lookbackMonths(), 24)
                : 6;

        SavingsTargetRecommendationResponse recommendation = contextService.calculateSavingsRecommendation(user, lookback);
        LocalDate today = NaturalLanguageDateResolver.today();
        LocalDate fromDate = today.minusMonths(lookback);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Recommended monthly savings target is ₹%.2f (range: ₹%.2f to ₹%.2f, target savings rate: %.1f%% of income). Confidence: %s (data sufficiency: %s across %d months). ",
                recommendation.recommendedMonthlySavings(),
                recommendation.recommendedRangeMin(),
                recommendation.recommendedRangeMax(),
                recommendation.recommendedSavingsRate(),
                recommendation.confidence(),
                recommendation.dataSufficiency(),
                recommendation.historyMonths()));

        if (!recommendation.supportingFacts().isEmpty()) {
            sb.append("Supporting facts: ").append(String.join("; ", recommendation.supportingFacts())).append(".");
        }

        ToolResponseEnvelope<SavingsTargetRecommendationResponse> envelope = ToolResponseEnvelope.of(
                recommendation,
                fromDate,
                today,
                "SAVINGS_TARGET",
                sb.toString(),
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input(
            Integer lookbackMonths
    ) {}
}
