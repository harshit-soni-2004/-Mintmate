package com.spendsense.categorization.rule;

import java.util.Optional;

/**
 * Interface contract for deterministic, explainable categorization rules.
 * Keeps business constraints modular and prevents unmaintainable giant dictionaries.
 */
public interface CategorizationRule {

    /**
     * Unique identifier for this rule (e.g. "RULE_PAYROLL", "RULE_TAX").
     */
    String getId();

    /**
     * Execution order / priority (lower values evaluate earlier).
     */
    int getOrder();

    /**
     * Evaluates whether this rule matches the given transaction context.
     *
     * @param context transaction context
     * @return match result if triggered, empty otherwise
     */
    Optional<RuleMatchResult> evaluate(RuleContext context);
}
