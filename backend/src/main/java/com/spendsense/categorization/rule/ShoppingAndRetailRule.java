package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic rule mapping major e-commerce platforms and retail stores to SHOPPING.
 * Disambiguates Amazon retail from Amazon Prime Video / AWS.
 */
@Component
public class ShoppingAndRetailRule implements CategorizationRule {

    private static final Set<String> SHOPPING_MERCHANTS = Set.of(
            "amazon", "amzn mktpl", "amzn mktpl india", "amazon seller", "amazon pay",
            "flipkart", "myntra", "ajio", "nykaa", "meesho", "tata cliq",
            "croma", "reliance digital", "vijay sales", "decathlon", "ikea",
            "dmart", "d mart", "avenue supermarts", "westside", "pantaloons",
            "zudio", "shoppers stop", "h&m", "zara", "uniqlo", "lifestyle"
    );

    private static final Pattern SHOPPING_PATTERN = Pattern.compile(
            "\\b(amazon(\\s*india)?|amzn\\s*mktpl|flipkart|myntra|ajio|nykaa|meesho|tata\\s*cliq|croma|reliance\\s*digital|vijay\\s*sales|decathlon|ikea|d\\s*mart|dmart|zudio|shoppers\\s*stop|h&m|zara|uniqlo)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getId() {
        return "RULE_SHOPPING_RETAIL";
    }

    @Override
    public int getOrder() {
        return 80;
    }

    @Override
    public Optional<RuleMatchResult> evaluate(RuleContext context) {
        if (context == null || context.normalizedTransaction() == null) {
            return Optional.empty();
        }

        String merchant = context.normalizedTransaction().normalizedMerchant();
        String composite = context.normalizedTransaction().compositeText();
        String rawDesc = context.normalizedTransaction().rawDescription();
        String rawMerchant = context.normalizedTransaction().rawMerchant();

        // Disambiguate Amazon Prime Video / AWS
        if (isStreamingOrCloudContext(composite, rawDesc, rawMerchant)) {
            return Optional.empty();
        }

        // 1. Direct canonical merchant match
        if (merchant != null && SHOPPING_MERCHANTS.contains(merchant.toLowerCase())) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.SHOPPING,
                    "Matched shopping/retail merchant: " + merchant
            ));
        }

        // 2. Pattern match
        if (matchesPattern(SHOPPING_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.SHOPPING,
                    "Matched shopping/retail brand pattern"
            ));
        }

        return Optional.empty();
    }

    private boolean matchesPattern(Pattern pattern, String merchant, String composite, String rawDesc, String rawMerchant) {
        if (merchant != null && pattern.matcher(merchant).find()) return true;
        if (composite != null && pattern.matcher(composite).find()) return true;
        if (rawDesc != null && pattern.matcher(rawDesc).find()) return true;
        if (rawMerchant != null && pattern.matcher(rawMerchant).find()) return true;
        return false;
    }

    private boolean isStreamingOrCloudContext(String composite, String rawDesc, String rawMerchant) {
        String combined = ((composite != null ? composite : "") + " "
                + (rawDesc != null ? rawDesc : "") + " "
                + (rawMerchant != null ? rawMerchant : "")).toLowerCase();
        return combined.contains("prime video") || combined.contains("amazon aws")
                || combined.contains("aws cloud") || combined.contains("amazon web services");
    }
}
