package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.analytics.AnomalyDetectionService;
import com.spendsense.analytics.dto.SpendingAnomaly;
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
public class IdentifyAnomaliesTool implements FinancialTool<IdentifyAnomaliesTool.Input, List<SpendingAnomaly>> {

    public static final String NAME = "identify_anomalies";

    private final AnomalyDetectionService anomalyDetectionService;

    public IdentifyAnomaliesTool(AnomalyDetectionService anomalyDetectionService) {
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Detect and explain unusual transactions and spending anomalies (spending spikes, sudden category surges, and individual transactions significantly exceeding average spending) for a date range.";
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
    public ToolExecutionResult<List<SpendingAnomaly>> execute(Input input, SpendSenseUserPrincipal user) {
        LocalDate start = input.startDate() != null ? input.startDate() : NaturalLanguageDateResolver.today().withDayOfMonth(1);
        LocalDate end = input.endDate() != null ? input.endDate() : NaturalLanguageDateResolver.today();

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        List<SpendingAnomaly> anomalies = anomalyDetectionService.detectAnomalies(start, end);
        if (anomalies == null) {
            anomalies = Collections.emptyList();
        }

        String summaryText;
        if (anomalies.isEmpty()) {
            summaryText = String.format("No unusual spending or anomalies detected from %s to %s. All transactions align with normal spending patterns.", start, end);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d spending anomal%s from %s to %s: ",
                    anomalies.size(), anomalies.size() == 1 ? "y" : "ies", start, end));
            for (int i = 0; i < anomalies.size(); i++) {
                SpendingAnomaly a = anomalies.get(i);
                if (i > 0) sb.append("; ");
                sb.append(String.format("[%s]: %s (amount: ₹%.2f, reason: %s)",
                        a.type(), a.title(), a.amount(), a.reason()));
            }
            sb.append(".");
            summaryText = sb.toString();
        }

        ToolResponseEnvelope<List<SpendingAnomaly>> envelope = ToolResponseEnvelope.of(
                anomalies,
                start,
                end,
                "ANOMALIES",
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
