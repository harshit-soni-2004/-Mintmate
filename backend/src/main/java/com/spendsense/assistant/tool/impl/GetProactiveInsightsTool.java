package com.spendsense.assistant.tool.impl;

import com.spendsense.assistant.proactive.ProactiveInsightDto;
import com.spendsense.assistant.proactive.ProactiveInsightService;
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
public class GetProactiveInsightsTool implements FinancialTool<GetProactiveInsightsTool.Input, List<ProactiveInsightDto>> {

    public static final String NAME = "get_proactive_insights";

    private final ProactiveInsightService proactiveInsightService;

    public GetProactiveInsightsTool(ProactiveInsightService proactiveInsightService) {
        this.proactiveInsightService = proactiveInsightService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve active proactive financial intelligence, including category spending spikes, budget warnings or exceeded limits, savings rate shifts, large transaction anomalies, and goals at risk.";
    }

    @Override
    public Class<Input> getInputType() {
        return Input.class;
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of()
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<List<ProactiveInsightDto>> execute(Input input, SpendSenseUserPrincipal user) {
        List<ProactiveInsightDto> insights = proactiveInsightService.getActiveInsights();
        if (insights == null) {
            insights = Collections.emptyList();
        }

        LocalDate today = NaturalLanguageDateResolver.today();
        String summaryText;
        if (insights.isEmpty()) {
            summaryText = "No proactive financial alerts or anomalies detected. All spending patterns, budgets, and savings goals are currently within normal baseline ranges.";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d active proactive financial insight%s: ", insights.size(), insights.size() == 1 ? "" : "s"));
            for (int i = 0; i < insights.size(); i++) {
                ProactiveInsightDto pi = insights.get(i);
                if (i > 0) sb.append("; ");
                sb.append(String.format("[%s] %s: %s", pi.severity(), pi.title(), pi.summary()));
                if (pi.actionRecommendation() != null && !pi.actionRecommendation().isBlank()) {
                    sb.append(" (Recommendation: ").append(pi.actionRecommendation()).append(")");
                }
            }
            sb.append(".");
            summaryText = sb.toString();
        }

        ToolResponseEnvelope<List<ProactiveInsightDto>> envelope = ToolResponseEnvelope.of(
                insights,
                today,
                today,
                "PROACTIVE_INSIGHTS",
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input() {}
}
