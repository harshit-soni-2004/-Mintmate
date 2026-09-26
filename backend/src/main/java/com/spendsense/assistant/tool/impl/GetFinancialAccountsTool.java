package com.spendsense.assistant.tool.impl;

import com.spendsense.account.FinancialAccountService;
import com.spendsense.account.dto.FinancialAccountResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.security.SpendSenseUserPrincipal;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GetFinancialAccountsTool implements FinancialTool<GetFinancialAccountsTool.Input, List<FinancialAccountResponse>> {

    public static final String NAME = "get_financial_accounts";

    private final FinancialAccountService accountService;

    public GetFinancialAccountsTool(FinancialAccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "List all linked bank accounts, cards, and wallets belonging to the user, including institution name, account type, and last 4 digits.";
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
                        "activeOnly", Map.of("type", "boolean", "default", true, "description", "Filter only active accounts")
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<List<FinancialAccountResponse>> execute(Input input, SpendSenseUserPrincipal user) {
        boolean activeOnly = input == null || input.activeOnly() == null || input.activeOnly();

        List<FinancialAccountResponse> accounts = accountService.list();
        if (activeOnly) {
            accounts = accounts.stream().filter(FinancialAccountResponse::active).toList();
        }

        String summaryText;
        if (accounts.isEmpty()) {
            summaryText = "You currently have no financial accounts linked.";
        } else {
            String accountNames = accounts.stream()
                    .map(a -> a.institutionName() + " (..." + (a.accountNumberLast4() != null ? a.accountNumberLast4() : "N/A") + ")")
                    .collect(Collectors.joining(", "));
            summaryText = String.format("You have %d linked account(s): %s.", accounts.size(), accountNames);
        }

        ToolResponseEnvelope<List<FinancialAccountResponse>> envelope = ToolResponseEnvelope.of(
                accounts,
                summaryText
        );

        return ToolExecutionResult.success(envelope);
    }

    public record Input(Boolean activeOnly) {}
}
