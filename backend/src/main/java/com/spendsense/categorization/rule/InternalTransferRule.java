package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Deterministic rule mapping internal bank account transfers to OTHER.
 */
@Component
public class InternalTransferRule implements CategorizationRule {

    private static final Set<String> TRANSFER_KEYWORDS = Set.of(
            "internal transfer", "transfer to savings", "self transfer",
            "account transfer", "internal xfer", "xfer to chk", "xfer to sav"
    );

    @Override
    public String getId() {
        return "RULE_INTERNAL_TRANSFER";
    }

    @Override
    public int getOrder() {
        return 50;
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

        for (String keyword : TRANSFER_KEYWORDS) {
            if (composite.contains(keyword)) {
                return Optional.of(RuleMatchResult.matched(
                        getId(),
                        TransactionCategory.OTHER,
                        "Matched deterministic internal account transfer rule (" + keyword + ")"
                ));
            }
        }

        return Optional.empty();
    }
}
