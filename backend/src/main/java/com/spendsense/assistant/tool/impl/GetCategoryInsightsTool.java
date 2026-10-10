package com.spendsense.assistant.tool.impl;

import com.spendsense.assistant.context.FinancialContextService;
import com.spendsense.assistant.dto.CategoryInsightsResponse;
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
public class GetCategoryInsightsTool implements FinancialTool<GetCategoryInsightsTool.Input, CategoryInsightsResponse> {

    public static final String NAME = "get_category_insights";

    private final FinancialContextService contextService;

    public GetCategoryInsightsTool(FinancialContextService contextService) {
        this.contextService = contextService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve deterministic category insights: identify highest spend categories, fastest growing categories, volatility patterns, and categories consistently above historical averages.";
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
    public ToolExecutionResult<CategoryInsightsResponse> execute(Input input, SpendSenseUserPrincipal user) {
        int lookback = input != null && input.lookbackMonths() != null && input.lookbackMonths() > 0
                ? Math.min(input.lookbackMonths(), 24)
                : 6;

        CategoryInsightsResponse response = contextService.getCategoryInsights(user, lookback);
        LocalDate today = NaturalLanguageDateResolver.today();
        LocalDate fromDate = today.minusMonths(lookback);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Category insights (%d-month lookback, sufficiency: %s): Highest spend category is %s, most volatile is %s, and fastest growing is %s.",
                lookback,
                response.dataSufficiency(),
                response.highestSpendCategory(),
                response.mostVolatileCategory(),
                response.fastestGrowingCategory()));

        if (!response.insights().isEmpty()) {
            sb.append(" Details: ").append(String.join("; ", response.insights())).append(".");
        }

        ToolResponseEnvelope<CategoryInsightsResponse> envelope = ToolResponseEnvelope.of(
                response,
                fromDate,
                today,
                "CATEGORY_INSIGHTS",
                sb.toString(),
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input(
            Integer lookbackMonths
    ) {}
}
