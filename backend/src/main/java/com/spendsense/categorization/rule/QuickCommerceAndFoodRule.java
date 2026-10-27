package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic rule mapping quick commerce groceries, food delivery, and restaurants to FOOD.
 * Disambiguates Blinkit (not 'blink' alone), BigBasket, Swiggy, Zomato, Zepto, and dining chains.
 */
@Component
public class QuickCommerceAndFoodRule implements CategorizationRule {

    private static final Set<String> FOOD_MERCHANTS = Set.of(
            "blinkit", "zepto", "bigbasket", "bb daily", "bb now", "bbinstant",
            "swiggy", "swiggy instamart", "swiggy dineout", "swiggy genie", "instamart",
            "zomato", "zomato online", "zomato feeding",
            "mcdonalds", "mcdonald's", "dominos", "domino's pizza", "pizza hut",
            "kfc", "burger king", "subway", "starbucks", "cafe coffee day", "ccd",
            "third wave coffee", "chaayos", "chai point", "barbeque nation", "haldiram", "haldirams",
            "dunzo", "dunzo daily", "eatfit", "box8", "faasos", "behrouz biryani", "biryani blues",
            "bikanervala", "paradise biryani", "wow momo", "rebel foods", "natures basket", "jiomart"
    );

    // Exact word patterns for ambiguous names
    private static final Pattern BLINKIT_PATTERN = Pattern.compile(
            "\\b(blinkit|blink\\s*it)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZEPTO_PATTERN = Pattern.compile(
            "\\b(zepto|zepto\\s*now)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIGBASKET_PATTERN = Pattern.compile(
            "\\b(big\\s*basket|bigbasket|bb\\s*daily|bb\\s*now|bbinstant)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SWIGGY_PATTERN = Pattern.compile(
            "\\b(swiggy|instamart)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZOMATO_PATTERN = Pattern.compile(
            "\\b(zomato)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UBER_EATS_PATTERN = Pattern.compile(
            "\\b(uber\\s*eats?)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern DINING_PATTERN = Pattern.compile(
            "\\b(mcdonalds?|dominos?(\\s*pizza)?|pizza\\s*hut|burger\\s*king|starbucks|third\\s*wave\\s*coffee|chaayos|chai\\s*point|barbeque\\s*nation|haldirams?|subway|faasos|behrouz\\s*biryani|eatfit|box8)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getId() {
        return "RULE_QUICK_COMMERCE_FOOD";
    }

    @Override
    public int getOrder() {
        return 60;
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
        if (merchant != null && FOOD_MERCHANTS.contains(merchant.toLowerCase())) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.FOOD,
                    "Matched quick commerce / food merchant: " + merchant
            ));
        }

        // 2. Blinkit (must NOT match "blink" alone)
        if (matchesPattern(BLINKIT_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.FOOD,
                    "Matched Blinkit quick commerce grocery delivery"
            ));
        }

        // 3. Zepto
        if (matchesPattern(ZEPTO_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.FOOD,
                    "Matched Zepto 10-minute grocery delivery"
            ));
        }

        // 4. BigBasket
        if (matchesPattern(BIGBASKET_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.FOOD,
                    "Matched BigBasket grocery delivery"
            ));
        }

        // 5. Swiggy
        if (matchesPattern(SWIGGY_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.FOOD,
                    "Matched Swiggy food & grocery delivery"
            ));
        }

        // 6. Zomato
        if (matchesPattern(ZOMATO_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.FOOD,
                    "Matched Zomato food delivery & dining"
            ));
        }

        // 7. Uber Eats
        if (matchesPattern(UBER_EATS_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.FOOD,
                    "Matched Uber Eats food delivery"
            ));
        }

        // 8. Dining chains
        if (matchesPattern(DINING_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.FOOD,
                    "Matched restaurant / cafe dining pattern"
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
