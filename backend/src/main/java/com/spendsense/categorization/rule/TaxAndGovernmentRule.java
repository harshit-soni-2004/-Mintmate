package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Deterministic rule mapping tax assessments and government revenue payments to BILLS.
 */
@Component
public class TaxAndGovernmentRule implements CategorizationRule {

    private static final Set<String> TAX_KEYWORDS = Set.of(
            "irs usatax", "state tax", "tax payment", "property tax", "dept of revenue",
            "income tax", "franchise tax board", "treasury tax"
    );

    @Override
    public String getId() {
        return "RULE_TAX_GOVERNMENT";
    }

    @Override
    public int getOrder() {
        return 20;
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

        for (String keyword : TAX_KEYWORDS) {
            if (composite.contains(keyword)) {
                return Optional.of(RuleMatchResult.matched(
                        getId(),
                        TransactionCategory.BILLS,
                        "Matched deterministic tax/government revenue payment rule (" + keyword + ")"
                ));
            }
        }

        return Optional.empty();
    }
}
