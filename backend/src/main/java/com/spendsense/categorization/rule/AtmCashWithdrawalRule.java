package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Deterministic rule detecting standalone ATM cash withdrawals.
 */
@Component
public class AtmCashWithdrawalRule implements CategorizationRule {

    private static final Set<String> ATM_KEYWORDS = Set.of(
            "atm wdl", "atm withdrawal", "atm cash", "cash withdrawal"
    );

    @Override
    public String getId() {
        return "RULE_ATM_CASH_WITHDRAWAL";
    }

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    public Optional<RuleMatchResult> evaluate(RuleContext context) {
        if (context == null || context.normalizedTransaction() == null) {
            return Optional.empty();
        }

        String merchant = context.normalizedTransaction().normalizedMerchant();
        String composite = context.normalizedTransaction().compositeText();

        if ("atm".equalsIgnoreCase(merchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.OTHER,
                    "Matched deterministic ATM withdrawal rule (merchant=atm)"
            ));
        }

        if (composite != null && !composite.isBlank()) {
            for (String keyword : ATM_KEYWORDS) {
                if (composite.contains(keyword)) {
                    return Optional.of(RuleMatchResult.matched(
                            getId(),
                            TransactionCategory.OTHER,
                            "Matched deterministic ATM withdrawal rule (" + keyword + ")"
                    ));
                }
            }
        }

        return Optional.empty();
    }
}
