package com.spendsense.assistant.tool.impl.mutation;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.action.PendingActionService;
import com.spendsense.assistant.dto.PendingActionDto;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.common.exception.ApiException;
import com.spendsense.goal.GoalService;
import com.spendsense.goal.dto.GoalRequest;
import com.spendsense.goal.dto.GoalResponse;
import com.spendsense.security.SpendSenseUserPrincipal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class CreateOrUpdateGoalTool implements FinancialTool<CreateOrUpdateGoalTool.Input, GoalResponse> {

    public static final String NAME = "create_or_update_goal";

    private final GoalService goalService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    public CreateOrUpdateGoalTool(
            GoalService goalService,
            @Lazy PendingActionService pendingActionService,
            ObjectMapper objectMapper
    ) {
        this.goalService = goalService;
        this.pendingActionService = pendingActionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Propose creating a new financial savings goal. Requires explicit user confirmation.";
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
                        "name", Map.of("type", "string", "description", "Name of the savings goal, e.g. 'Emergency Fund'"),
                        "targetAmount", Map.of("type", "number", "minimum", 1, "description", "Target savings amount in INR"),
                        "targetDate", Map.of("type", "string", "format", "date", "description", "Target completion date YYYY-MM-DD"),
                        "currentAmount", Map.of("type", "number", "minimum", 0, "description", "Initial amount currently saved, optional")
                ),
                "required", List.of("name", "targetAmount", "targetDate")
        );
    }

    @Override
    public boolean isMutating() {
        return true;
    }

    @Override
    public ToolExecutionResult<GoalResponse> execute(Input input, SpendSenseUserPrincipal user) {
        LocalDate today = NaturalLanguageDateResolver.today();
        if (input.targetDate().isBefore(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_DATE", "Goal target date must be in the future");
        }

        String summary = String.format("Create savings goal '%s' of ₹%.2f by %s",
                input.name().trim(), input.targetAmount(), input.targetDate());

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

        GoalRequest request = new GoalRequest(
                input.name().trim(),
                input.targetAmount(),
                input.currentAmount() != null ? input.currentAmount() : BigDecimal.ZERO,
                input.targetDate()
        );

        return goalService.create(request);
    }

    public record Input(
            @NotBlank(message = "name is required")
            String name,

            @NotNull(message = "targetAmount is required")
            @DecimalMin(value = "1.00", message = "targetAmount must be at least 1.00")
            BigDecimal targetAmount,

            @NotNull(message = "targetDate is required")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate targetDate,

            BigDecimal currentAmount
    ) {}
}
