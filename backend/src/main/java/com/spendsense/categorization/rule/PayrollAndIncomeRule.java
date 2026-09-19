package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Deterministic rule detecting salary, payroll, and direct deposit income entries.
 */
@Component
public class PayrollAndIncomeRule implements CategorizationRule {

    private static final Set<String> PAYROLL_KEYWORDS = Set.of(
            "payroll", "salary", "direct dep", "dir dep", "stipend", "wages", "bonus payout"
    );

    @Override
    public String getId() {
        return "RULE_PAYROLL_INCOME";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public Optional<RuleMatchResult> evaluate(RuleContext context) {
        if (context == null || context.normalizedTransaction() == null) {
            return Optional.empty();
        }

        String composite = context.normalizedTransaction().compositeText();
        if (composite == null || composite.isBlank()) {
            return Optional.empty();
        }

        for (String keyword : PAYROLL_KEYWORDS) {
            if (composite.contains(keyword)) {
                return Optional.of(RuleMatchResult.matched(
                        getId(),
                        TransactionCategory.OTHER,
                        "Matched deterministic payroll/salary credit rule (" + keyword + ")"
                ));
            }
        }

        return Optional.empty();
    }
}
