package com.spendsense.categorization.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic rule engine orchestrating modular categorization rules.
 * Executes rules in strictly ordered precedence.
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<CategorizationRule> rules;

    public RuleEngine(List<CategorizationRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(CategorizationRule::getOrder))
                .toList();
        log.info("Initialized RuleEngine with {} registered rules", this.rules.size());
    }

    /**
     * Evaluates registered rules against the context in ascending order.
     * Returns the first matching rule result, or empty if no rule triggers.
     */
    public Optional<RuleMatchResult> evaluate(RuleContext context) {
        if (context == null) {
            return Optional.empty();
        }

        for (CategorizationRule rule : rules) {
            Optional<RuleMatchResult> match = rule.evaluate(context);
            if (match.isPresent()) {
                log.debug("Rule matched: {} for merchant='{}'", rule.getId(),
                        context.normalizedTransaction() != null ? context.normalizedTransaction().normalizedMerchant() : "null");
                return match;
            }
        }

        return Optional.empty();
    }

    public List<CategorizationRule> getRegisteredRules() {
        return rules;
    }
}
