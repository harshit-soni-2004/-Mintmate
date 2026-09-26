package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionService;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferDirection;
import com.spendsense.transaction.TransferMatchStatus;
import com.spendsense.transaction.dto.TransactionResponse;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SearchTransactionsTool implements FinancialTool<SearchTransactionsTool.Input, SearchTransactionsTool.SearchResult> {

    public static final String NAME = "search_transactions";

    private final TransactionService transactionService;

    public SearchTransactionsTool(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Search, filter, and paginate transactions by merchant name, category, transaction type (EXPENSE, INCOME, TRANSFER, INVESTMENT, CASH_WITHDRAWAL), date range, and amount range.";
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
                        "startDate", Map.of("type", "string", "format", "date", "description", "Optional start date YYYY-MM-DD"),
                        "endDate", Map.of("type", "string", "format", "date", "description", "Optional end date YYYY-MM-DD"),
                        "category", Map.of("type", "string", "enum", List.of("FOOD", "TRAVEL", "SHOPPING", "ENTERTAINMENT", "BILLS", "HEALTH", "EDUCATION", "PERSONAL", "OTHER")),
                        "transactionType", Map.of("type", "string", "enum", List.of("EXPENSE", "INCOME", "TRANSFER", "INVESTMENT", "CASH_WITHDRAWAL")),
                        "search", Map.of("type", "string", "description", "Optional keyword search for merchant or description"),
                        "minAmount", Map.of("type", "number", "minimum", 0),
                        "maxAmount", Map.of("type", "number", "minimum", 0),
                        "page", Map.of("type", "integer", "minimum", 0, "default", 0),
                        "size", Map.of("type", "integer", "minimum", 1, "maximum", 50, "default", 10)
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<SearchResult> execute(Input input, SpendSenseUserPrincipal user) {
        int page = input.page() != null && input.page() >= 0 ? input.page() : 0;
        int size = input.size() != null && input.size() > 0 ? Math.min(input.size(), 50) : 10;

        TransactionService.TransactionPage pageResult = transactionService.list(
                page,
                size,
                input.category(),
                input.transactionType(),
                null,
                input.startDate(),
                input.endDate(),
                input.minAmount(),
                input.maxAmount(),
                input.search(),
                "newest",
                null,
                null
        );

        // Sanitize financial text by encapsulating in literal tags (DATA != INSTRUCTIONS)
        List<SanitizedTransactionItem> sanitizedItems = pageResult.content().stream()
                .map(this::toSanitizedItem)
                .toList();

        SearchResult result = new SearchResult(
                sanitizedItems,
                pageResult.meta().page(),
                pageResult.meta().size(),
                pageResult.meta().totalElements(),
                pageResult.meta().totalPages()
        );

        String summaryText = String.format("Found %d matching transactions (showing page %d of %d).",
                pageResult.meta().totalElements(),
                pageResult.meta().page() + 1,
                Math.max(1, pageResult.meta().totalPages()));

        ToolResponseEnvelope<SearchResult> envelope = ToolResponseEnvelope.of(
                result,
                input.startDate(),
                input.endDate(),
                "TRANSACTIONS",
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    private SanitizedTransactionItem toSanitizedItem(TransactionResponse tx) {
        String safeMerchant = tx.merchant() != null
                ? "<untrusted_merchant_text>" + tx.merchant().replaceAll("(?i)</?untrusted_merchant_text>", "").trim() + "</untrusted_merchant_text>"
                : null;
        String safeDesc = tx.description() != null
                ? "<untrusted_merchant_text>" + tx.description().replaceAll("(?i)</?untrusted_merchant_text>", "").trim() + "</untrusted_merchant_text>"
                : null;

        String accountName = tx.accountName();
        String counterpartyName = tx.counterpartyAccountName();

        return new SanitizedTransactionItem(
                tx.id(),
                tx.amount(),
                tx.transactionDate(),
                tx.category(),
                tx.transactionType(),
                tx.transferDirection(),
                tx.transferMatchStatus(),
                safeMerchant,
                safeDesc,
                accountName,
                counterpartyName
        );
    }

    public record Input(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate startDate,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate endDate,

            TransactionCategory category,
            TransactionType transactionType,

            @Size(max = 100, message = "Search keyword must not exceed 100 characters")
            String search,

            BigDecimal minAmount,
            BigDecimal maxAmount,
            Integer page,
            Integer size
    ) {}

    public record SearchResult(
            List<SanitizedTransactionItem> transactions,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record SanitizedTransactionItem(
            UUID id,
            BigDecimal amount,
            LocalDate date,
            TransactionCategory category,
            TransactionType type,
            TransferDirection transferDirection,
            TransferMatchStatus transferMatchStatus,
            String merchant,
            String description,
            String accountName,
            String counterpartyAccountName
    ) {}
}
