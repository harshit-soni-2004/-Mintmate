package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Deterministic rule mapping bank interest credits and dividend payouts to OTHER.
 */
@Component
public class BankInterestAndDividendRule implements CategorizationRule {

    private static final Set<String> INTEREST_KEYWORDS = Set.of(
            "interest credit", "interest payment", "dividend paid", "dividend credit",
            "apy payout", "savings interest"
    );

    @Override
    public String getId() {
        return "RULE_BANK_INTEREST_DIVIDEND";
    }

    @Override
    public int getOrder() {
        return 30;
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

        for (String keyword : INTEREST_KEYWORDS) {
            if (composite.contains(keyword)) {
                return Optional.of(RuleMatchResult.matched(
                        getId(),
                        TransactionCategory.OTHER,
                        "Matched deterministic bank interest/dividend credit rule (" + keyword + ")"
                ));
            }
        }

        return Optional.empty();
    }
}
