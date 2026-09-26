package com.spendsense.assistant.tool.impl.mutation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.action.PendingActionService;
import com.spendsense.assistant.dto.PendingActionDto;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.budget.BudgetPeriod;
import com.spendsense.budget.BudgetService;
import com.spendsense.budget.dto.BudgetRequest;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CreateOrUpdateBudgetTool implements FinancialTool<CreateOrUpdateBudgetTool.Input, BudgetResponse> {

    public static final String NAME = "create_or_update_budget";

    private final BudgetService budgetService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    public CreateOrUpdateBudgetTool(
            BudgetService budgetService,
            @Lazy PendingActionService pendingActionService,
            ObjectMapper objectMapper
    ) {
        this.budgetService = budgetService;
        this.pendingActionService = pendingActionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Propose creating or updating a monthly budget limit for a spending category. Requires explicit user confirmation.";
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
                        "limitAmount", Map.of("type", "number", "minimum", 1, "description", "Monthly limit amount in INR"),
                        "yearMonth", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$", "description", "Optional target month in YYYY-MM format. Defaults to current month.")
                ),
                "required", List.of("category", "limitAmount")
        );
    }

    @Override
    public boolean isMutating() {
        return true;
    }

    @Override
    public ToolExecutionResult<BudgetResponse> execute(Input input, SpendSenseUserPrincipal user) {
        String ym = (input.yearMonth() != null && !input.yearMonth().isBlank())
                ? input.yearMonth().trim()
                : YearMonth.from(NaturalLanguageDateResolver.today()).toString();

        List<BudgetResponse> existingBudgets = budgetService.list(ym, null, null);
        Optional<BudgetResponse> existing = existingBudgets.stream()
                .filter(b -> b.category() == input.category())
                .findFirst();

        String summary;
        if (existing.isPresent()) {
            summary = String.format("Update monthly %s budget for %s from ₹%.2f to ₹%.2f",
                    input.category().getDisplayName(), ym, existing.get().limitAmount(), input.limitAmount());
        } else {
            summary = String.format("Set new monthly %s budget of ₹%.2f for %s",
                    input.category().getDisplayName(), input.limitAmount(), ym);
        }

        PendingActionDto pendingAction = pendingActionService.createPendingAction(NAME, input, summary);
        return ToolExecutionResult.confirmationRequired(summary, pendingAction);
    }

    @Override
    public Object executeConfirmedMutation(String parametersJson, SpendSenseUserPrincipal user) {
        Input input;
        try {
            input = objectMapper.readValue(parametersJson, Input.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETERS", "Failed to deserialize input: " + e.getMessage());
        }

        String ym = (input.yearMonth() != null && !input.yearMonth().isBlank())
                ? input.yearMonth().trim()
                : YearMonth.from(NaturalLanguageDateResolver.today()).toString();

        YearMonth yearMonth = YearMonth.parse(ym);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<BudgetResponse> existingBudgets = budgetService.list(ym, null, null);
        Optional<BudgetResponse> existing = existingBudgets.stream()
                .filter(b -> b.category() == input.category())
                .findFirst();

        BudgetRequest request = new BudgetRequest(
                input.limitAmount(),
                input.category(),
                BudgetPeriod.MONTHLY,
                start,
                end
        );

        if (existing.isPresent()) {
            return budgetService.update(existing.get().id(), request);
        } else {
            return budgetService.create(request);
        }
    }

    public record Input(
            @NotNull(message = "category is required")
            TransactionCategory category,

            @NotNull(message = "limitAmount is required")
            @DecimalMin(value = "1.00", message = "limitAmount must be at least 1.00")
            BigDecimal limitAmount,

            String yearMonth
    ) {}
}
