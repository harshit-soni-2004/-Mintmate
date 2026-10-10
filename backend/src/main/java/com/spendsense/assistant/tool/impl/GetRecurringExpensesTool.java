package com.spendsense.assistant.tool.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.spendsense.assistant.dto.RecurringExpenseItem;
import com.spendsense.assistant.dto.RecurringExpensesResponse;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolExecutionResult;
import com.spendsense.assistant.tool.ToolResponseEnvelope;
import com.spendsense.assistant.tool.date.NaturalLanguageDateResolver;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GetRecurringExpensesTool implements FinancialTool<GetRecurringExpensesTool.Input, RecurringExpensesResponse> {

    public static final String NAME = "get_recurring_expenses";

    private final TransactionRepository transactionRepository;

    public GetRecurringExpensesTool(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Identify and list recurring expenses, monthly subscriptions, and repeating bill charges detected across your transaction history with confidence levels.";
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
                        "lookbackMonths", Map.of("type", "integer", "description", "Number of lookback months to analyze (default: 6)"),
                        "minConfidence", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH"), "description", "Minimum confidence filter")
                )
        );
    }

    @Override
    public boolean isMutating() {
        return false;
    }

    @Override
    public ToolExecutionResult<RecurringExpensesResponse> execute(Input input, SpendSenseUserPrincipal user) {
        int lookbackMonths = (input != null && input.lookbackMonths() != null && input.lookbackMonths() > 0)
                ? Math.min(input.lookbackMonths(), 12)
                : 6;

        LocalDate toDate = NaturalLanguageDateResolver.today();
        LocalDate fromDate = toDate.minusMonths(lookbackMonths);

        List<Transaction> transactions = transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(
                user.getUserId(), TransactionType.EXPENSE, fromDate, toDate);

        // Group by normalized merchant
        Map<String, List<Transaction>> merchantGroups = transactions.stream()
                .filter(t -> t.getMerchant() != null && !t.getMerchant().isBlank())
                .collect(Collectors.groupingBy(t -> normalizeMerchant(t.getMerchant())));

        List<RecurringExpenseItem> items = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : merchantGroups.entrySet()) {
            List<Transaction> txs = entry.getValue();
            if (txs.size() < 2) continue;

            txs.sort(Comparator.comparing(Transaction::getTransactionDate));

            // Calculate amounts
            BigDecimal totalAmt = BigDecimal.ZERO;
            BigDecimal minAmt = txs.getFirst().getAmount();
            BigDecimal maxAmt = txs.getFirst().getAmount();

            for (Transaction tx : txs) {
                totalAmt = totalAmt.add(tx.getAmount());
                if (tx.getAmount().compareTo(minAmt) < 0) minAmt = tx.getAmount();
                if (tx.getAmount().compareTo(maxAmt) > 0) maxAmt = tx.getAmount();
            }

            BigDecimal avgAmt = totalAmt.divide(BigDecimal.valueOf(txs.size()), 2, RoundingMode.HALF_UP);

            // Calculate intervals
            long totalDays = 0;
            for (int i = 1; i < txs.size(); i++) {
                totalDays += ChronoUnit.DAYS.between(txs.get(i - 1).getTransactionDate(), txs.get(i).getTransactionDate());
            }
            long avgIntervalDays = totalDays / (txs.size() - 1);

            String frequency;
            if (avgIntervalDays >= 25 && avgIntervalDays <= 35) {
                frequency = "MONTHLY";
            } else if (avgIntervalDays >= 6 && avgIntervalDays <= 8) {
                frequency = "WEEKLY";
            } else {
                frequency = "IRREGULAR_REPEATED";
            }

            // Calculate confidence
            BigDecimal amountSpread = maxAmt.subtract(minAmt);
            BigDecimal spreadRatio = avgAmt.compareTo(BigDecimal.ZERO) > 0
                    ? amountSpread.divide(avgAmt, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String confidence;
            if (amountSpread.compareTo(BigDecimal.ZERO) == 0 && txs.size() >= 3 && "MONTHLY".equals(frequency)) {
                confidence = "HIGH";
            } else if (spreadRatio.compareTo(new BigDecimal("0.12")) <= 0 && txs.size() >= 2) {
                confidence = "MEDIUM";
            } else if (spreadRatio.compareTo(new BigDecimal("0.30")) <= 0 && txs.size() >= 2) {
                confidence = "LOW";
            } else {
                continue; // insufficient recurrence pattern
            }

            String displayMerchant = txs.getLast().getMerchant();
            items.add(new RecurringExpenseItem(
                    displayMerchant,
                    txs.getLast().getCategory(),
                    avgAmt,
                    txs.getLast().getAmount(),
                    frequency,
                    confidence,
                    txs.size(),
                    txs.getLast().getTransactionDate()
            ));
        }

        // Filter by min confidence if specified
        if (input != null && input.minConfidence() != null && !input.minConfidence().isBlank()) {
            String target = input.minConfidence().toUpperCase(Locale.ROOT);
            if ("HIGH".equals(target)) {
                items.removeIf(item -> !"HIGH".equals(item.confidence()));
            } else if ("MEDIUM".equals(target)) {
                items.removeIf(item -> "LOW".equals(item.confidence()));
            }
        }

        items.sort((a, b) -> b.averageAmount().compareTo(a.averageAmount()));

        BigDecimal totalMonthly = items.stream()
                .filter(i -> "MONTHLY".equals(i.frequency()))
                .map(RecurringExpenseItem::averageAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int periodDays = (int) ChronoUnit.DAYS.between(fromDate, toDate);
        RecurringExpensesResponse response = new RecurringExpensesResponse(totalMonthly, items, periodDays);

        String summaryText;
        if (items.isEmpty()) {
            summaryText = String.format("No recurring subscriptions or repeated expenses detected over the past %d months.", lookbackMonths);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Detected %d potential recurring expense%s totaling approx ₹%.2f/month: ",
                    items.size(), items.size() == 1 ? "" : "s", totalMonthly));
            for (int i = 0; i < items.size(); i++) {
                RecurringExpenseItem it = items.get(i);
                if (i > 0) sb.append("; ");
                sb.append(String.format("%s (avg ₹%.2f, %s, %s confidence)",
                        it.merchant(), it.averageAmount(), it.frequency().toLowerCase(), it.confidence()));
            }
            sb.append(".");
            summaryText = sb.toString();
        }

        ToolResponseEnvelope<RecurringExpensesResponse> envelope = ToolResponseEnvelope.of(
                response,
                fromDate,
                toDate,
                "RECURRING",
                summaryText,
                Collections.emptyList()
        );

        return ToolExecutionResult.success(envelope);
    }

    private String normalizeMerchant(String raw) {
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        // Remove common UPI and payment gateway noise
        clean = clean.replaceAll("^(upi/|pos/|bil/|ach/|neft/|imps/)", "");
        clean = clean.replaceAll("[0-9]{6,}", "");
        clean = clean.replaceAll("[^a-z0-9\\s]", " ").trim();
        return clean.replaceAll("\\s+", " ");
    }

    public record Input(
            Integer lookbackMonths,
            String minConfidence
    ) {}
}
