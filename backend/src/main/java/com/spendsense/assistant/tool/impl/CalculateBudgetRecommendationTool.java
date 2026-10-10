package com.spendsense.assistant.tool.impl;

import com.spendsense.assistant.context.FinancialContextService;
import com.spendsense.assistant.dto.BudgetRecommendationResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CalculateBudgetRecommendationTool implements FinancialTool<CalculateBudgetRecommendationTool.Input, BudgetRecommendationResponse> {

    public static final String NAME = "calculate_budget_recommendation";

    private final FinancialContextService contextService;

    public CalculateBudgetRecommendationTool(FinancialContextService contextService) {
        this.contextService = contextService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Calculate a personalized, deterministic monthly budget recommendation for a category (e.g. Food, Travel, Shopping) grounded in historical category spend, recent trends, and overall cashflow.";
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
                        "category", Map.of("type", "string", "enum", List.of("FOOD", "TRAVEL", "SHOPPING", "ENTERTAINMENT", "BILLS", "HEALTH", "EDUCATION", "PERSONAL", "OTHER"), "description", "Category to budget for"),
                        "lookbackMonths", Map.of("type", "integer", "description", "Months of history to analyze (default: 6)")
                ),
                "required", List.of("category")
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<BudgetRecommendationResponse> execute(Input input, SpendSenseUserPrincipal user) {
        if (input == null || input.category() == null) {
            return ToolExecutionResult.failure("Category is required for budget recommendation.");
        }

        int lookback = input.lookbackMonths() != null && input.lookbackMonths() > 0
                ? Math.min(input.lookbackMonths(), 24)
                : 6;

        BudgetRecommendationResponse recommendation = contextService.calculateBudgetRecommendation(user, input.category(), lookback);
        LocalDate today = NaturalLanguageDateResolver.today();
        LocalDate fromDate = today.minusMonths(lookback);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Recommended monthly budget for %s is ₹%.2f (range: ₹%.2f to ₹%.2f). Confidence: %s (data sufficiency: %s across %d months). ",
                input.category().getDisplayName(),
                recommendation.recommendedBudget(),
                recommendation.recommendedRangeMin(),
                recommendation.recommendedRangeMax(),
                recommendation.confidence(),
                recommendation.dataSufficiency(),
                recommendation.historyMonths()));

        if (!recommendation.supportingFacts().isEmpty()) {
            sb.append("Supporting facts: ").append(String.join("; ", recommendation.supportingFacts())).append(".");
        }

        ToolResponseEnvelope<BudgetRecommendationResponse> envelope = ToolResponseEnvelope.of(
                recommendation,
                fromDate,
                today,
                "BUDGET_RECOMMENDATION",
                sb.toString(),
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input(
            @NotNull(message = "Category is required")
            TransactionCategory category,

            Integer lookbackMonths
    ) {}
}
