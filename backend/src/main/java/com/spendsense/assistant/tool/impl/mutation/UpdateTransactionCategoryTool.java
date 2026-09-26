package com.spendsense.assistant.tool.impl.mutation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.action.PendingActionService;
import com.spendsense.assistant.dto.PendingActionDto;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionService;
import com.spendsense.transaction.dto.TransactionResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class UpdateTransactionCategoryTool implements FinancialTool<UpdateTransactionCategoryTool.Input, TransactionResponse> {

    public static final String NAME = "update_transaction_category";

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    public UpdateTransactionCategoryTool(
            TransactionRepository transactionRepository,
            TransactionService transactionService,
            @Lazy PendingActionService pendingActionService,
            ObjectMapper objectMapper
    ) {
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.pendingActionService = pendingActionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Propose updating the category of an existing transaction. This requires user confirmation before taking effect.";
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
                        "transactionId", Map.of("type", "string", "format", "uuid", "description", "UUID of the transaction to update"),
                        "category", Map.of("type", "string", "enum", List.of("FOOD", "TRAVEL", "SHOPPING", "ENTERTAINMENT", "BILLS", "HEALTH", "EDUCATION", "PERSONAL", "OTHER"), "description", "New category to assign")
                ),
                "required", List.of("transactionId", "category")
        );
    }

    @Override
    public boolean isMutating() {
        return true;
    }

    @Override
    public ToolExecutionResult<TransactionResponse> execute(Input input, SpendSenseUserPrincipal user) {
        Transaction tx = transactionRepository.findById(input.transactionId())
                .filter(t -> t.getUser().getId().equals(user.getUserId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found or not owned by authenticated user"));

        String summary = String.format("Change category of %s (₹%.2f on %s) from %s to %s",
                tx.getMerchant() != null ? tx.getMerchant() : "transaction",
                tx.getAmount(),
                tx.getTransactionDate(),
                tx.getCategory() != null ? tx.getCategory().getDisplayName() : "Unknown",
                input.category().getDisplayName());

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

        return transactionService.correctCategory(input.transactionId(), input.category());
    }

    public record Input(
            @NotNull(message = "transactionId is required")
            UUID transactionId,

            @NotNull(message = "category is required")
            TransactionCategory category
    ) {}
}
