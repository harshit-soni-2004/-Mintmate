package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic rule mapping OTT subscriptions, streaming audio/video, and multiplex movie tickets to ENTERTAINMENT.
 */
@Component
public class EntertainmentAndStreamingRule implements CategorizationRule {

    private static final Set<String> ENTERTAINMENT_MERCHANTS = Set.of(
            "netflix", "spotify", "amazon prime video", "prime video",
            "disney hotstar", "hotstar", "sonyliv", "zee5", "youtube premium",
            "apple tv", "apple music", "gaana", "jiosaavn",
            "bookmyshow", "pvr", "pvr cinemas", "inox", "inox leisure", "cinepolis",
            "steam", "playstation", "playstation network", "xbox", "nintendo"
    );

    private static final Pattern ENTERTAINMENT_PATTERN = Pattern.compile(
            "\\b(netflix|spotify|prime\\s*video|disney\\s*\\+?\\s*hotstar|hotstar|sonyliv|zee5|youtube\\s*premium|bookmyshow|pvr(\\s*cinemas)?|inox|cinepolis|playstation|steamgames)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getId() {
        return "RULE_ENTERTAINMENT_STREAMING";
    }

    @Override
    public int getOrder() {
        return 85;
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

        // 1. Direct canonical merchant match
        if (merchant != null && ENTERTAINMENT_MERCHANTS.contains(merchant.toLowerCase())) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.ENTERTAINMENT,
                    "Matched entertainment/streaming provider: " + merchant
            ));
        }

        // 2. Pattern match
        if (matchesPattern(ENTERTAINMENT_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.ENTERTAINMENT,
                    "Matched entertainment/cinema/streaming pattern"
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
}
