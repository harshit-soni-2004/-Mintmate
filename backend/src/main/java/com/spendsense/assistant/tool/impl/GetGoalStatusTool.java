package com.spendsense.assistant.tool.impl;

import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.goal.GoalService;
import com.spendsense.goal.dto.GoalResponse;
import com.spendsense.security.SpendSenseUserPrincipal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GetGoalStatusTool implements FinancialTool<GetGoalStatusTool.Input, List<GoalResponse>> {

    public static final String NAME = "get_goal_status";

    private final GoalService goalService;

    public GetGoalStatusTool(GoalService goalService) {
        this.goalService = goalService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve the current status, target amount, accumulated savings, and percentage progress for all active financial savings goals.";
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
    public ToolExecutionResult<List<GoalResponse>> execute(Input input, SpendSenseUserPrincipal user) {
        List<GoalResponse> goals = goalService.list();
        if (goals == null) {
            goals = Collections.emptyList();
        }

        LocalDate today = NaturalLanguageDateResolver.today();

        String summaryText;
        if (goals.isEmpty()) {
            summaryText = "You currently do not have any active financial savings goals set up.";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("You have %d financial savings goal%s: ", goals.size(), goals.size() == 1 ? "" : "s"));
            for (int i = 0; i < goals.size(); i++) {
                GoalResponse g = goals.get(i);
                BigDecimal remaining = g.remainingAmount() != null ? g.remainingAmount() : g.targetAmount().subtract(g.currentAmount()).max(BigDecimal.ZERO);
                if (i > 0) sb.append("; ");
                sb.append(String.format("%s: ₹%.2f of ₹%.2f saved (%.1f%% complete, ₹%.2f remaining",
                        g.name(), g.currentAmount(), g.targetAmount(), g.progressPercentage(), remaining));
                if (g.targetDate() != null) {
                    sb.append(", target date: ").append(g.targetDate());
                }
                sb.append(")");
            }
            sb.append(".");
            summaryText = sb.toString();
        }

        ToolResponseEnvelope<List<GoalResponse>> envelope = ToolResponseEnvelope.of(
                goals,
                today,
                today,
                "GOALS",
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input() {}
}
