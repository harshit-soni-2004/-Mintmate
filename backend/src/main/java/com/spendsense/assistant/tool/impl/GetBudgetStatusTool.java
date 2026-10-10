package com.spendsense.assistant.tool.impl;

import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.budget.BudgetService;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.budget.dto.BudgetSummaryResponse;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GetBudgetStatusTool implements FinancialTool<GetBudgetStatusTool.Input, GetBudgetStatusTool.BudgetStatusResult> {

    public static final String NAME = "get_budget_status";

    private final BudgetService budgetService;

    public GetBudgetStatusTool(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve monthly budget limits, spending-to-date, remaining amounts, and alert status (ON_TRACK, WARNING, EXCEEDED) for a given month (YYYY-MM).";
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
                        "yearMonth", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$", "description", "Target month in YYYY-MM format. Defaults to current month if omitted."),
                        "category", Map.of("type", "string", "enum", List.of("FOOD", "TRAVEL", "SHOPPING", "ENTERTAINMENT", "BILLS", "HEALTH", "EDUCATION", "PERSONAL", "OTHER"), "description", "Optional category filter")
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<BudgetStatusResult> execute(Input input, SpendSenseUserPrincipal user) {
        String targetYm;
        if (input.yearMonth() != null && !input.yearMonth().isBlank()) {
            targetYm = NaturalLanguageDateResolver.resolveYearMonth(input.yearMonth().trim(), NaturalLanguageDateResolver.today()).toString();
        } else {
            targetYm = YearMonth.from(NaturalLanguageDateResolver.today()).toString();
        }

        BudgetSummaryResponse summary = budgetService.getSummary(targetYm, null, null);
        List<BudgetResponse> budgets = budgetService.list(targetYm, null, null);

        if (input.category() != null) {
            budgets = budgets.stream()
                    .filter(b -> b.category() == input.category())
                    .toList();
        }

        BudgetStatusResult result = new BudgetStatusResult(
                targetYm,
                summary,
                budgets
        );

        String summaryText;
        if (budgets.isEmpty()) {
            if (input.category() != null) {
                summaryText = String.format("You don't have an active %s budget configured for %s yet, so I cannot determine whether you're within budget. You can set a monthly limit to start tracking it.", input.category().getDisplayName(), targetYm);
            } else {
                summaryText = String.format("You don't have any active budgets configured for %s yet, so I cannot determine budget compliance. You can create a budget to start tracking your spending limits.", targetYm);
            }
        } else if (input.category() != null && !budgets.isEmpty()) {
            BudgetResponse b = budgets.getFirst();
            summaryText = String.format("For %s, %s budget is ₹%.2f with ₹%.2f spent (%.1f%% used), ₹%.2f remaining. Status: %s.",
                    targetYm, b.category().getDisplayName(), b.limitAmount(), b.spentAmount(), b.percentageUsed(), b.remainingAmount(), b.status());
        } else {
            summaryText = String.format("For %s, total budget is ₹%.2f with ₹%.2f spent (%.1f%% used), ₹%.2f remaining across %d budgets (%d on track, %d warning, %d exceeded).",
                    targetYm, summary.totalBudget(), summary.totalSpent(), summary.percentageUsed(), summary.totalRemaining(), summary.totalBudgets(), summary.onTrackCount(), summary.warningCount(), summary.exceededCount());
        }

        YearMonth ym = YearMonth.parse(targetYm);
        ToolResponseEnvelope<BudgetStatusResult> envelope = ToolResponseEnvelope.of(
                result,
                ym.atDay(1),
                ym.atEndOfMonth(),
                input.category() != null ? input.category().name() : "ALL_BUDGETS",
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input(
            String yearMonth,
            TransactionCategory category
    ) {}

    public record BudgetStatusResult(
            String yearMonth,
            BudgetSummaryResponse summary,
            List<BudgetResponse> budgets
    ) {}
}
