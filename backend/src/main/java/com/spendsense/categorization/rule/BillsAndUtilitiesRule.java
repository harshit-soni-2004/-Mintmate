package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic rule mapping electricity, water, piped gas/LPG, broadband, and telecom utilities to BILLS.
 * Carefully avoids false positives on telecom banking transfers (e.g. Airtel Payments Bank).
 */
@Component
public class BillsAndUtilitiesRule implements CategorizationRule {

    private static final Set<String> BILLS_MERCHANTS = Set.of(
            "bescom", "tata power", "adani electricity", "torrent power", "mahavitaran",
            "cesc", "bses", "dhbvn", "uppcl", "wbsedcl",
            "bwssb", "delhi jal board", "djb",
            "indane gas", "bharat gas", "hp gas", "mahanagar gas", "mgl", "igl", "gujarat gas",
            "act fibernet", "act fiber", "hathway", "tata play", "tata sky", "dish tv", "sun direct",
            "airtel broadband", "airtel dth", "airtel fiber", "airtel bill",
            "jiofiber", "jio fiber", "jio broadband", "jio recharge"
    );

    private static final Pattern UTILITY_KEYWORDS_PATTERN = Pattern.compile(
            "\\b(electricity\\s*bill|electric\\s*bill|power\\s*corp|power\\s*distribution|water\\s*bill|jal\\s*board|piped\\s*gas|gas\\s*utility|lpg\\s*cylinder|gas\\s*bill|broadband\\s*bill|dth\\s*recharge|mobile\\s*postpaid|postpaid\\s*bill|telecom\\s*bill)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern POWER_DISCOM_PATTERN = Pattern.compile(
            "\\b(bescom|tata\\s*power|adani\\s*electricity|torrent\\s*power|mahavitaran|msedcl|cesc|bses(\\s*rajdhani|\\s*yamuna)?|dhbvn|uppcl|wbsedcl|kseb|tneb)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GAS_WATER_PATTERN = Pattern.compile(
            "\\b(indane(\\s*gas)?|bharat\\s*gas|hp\\s*gas|mahanagar\\s*gas|mgl|igl|indraprastha\\s*gas|gujarat\\s*gas|bwssb|delhi\\s*jal\\s*board|djb)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TELECOM_PATTERN = Pattern.compile(
            "\\b(airtel|reliance\\s*jio|jio\\s*fiber|jiofiber|vodafone(\\s*idea)?|\\bvi\\b|idea\\s*cellular|bsnl|mtnl|act\\s*fiber(net)?|hathway|tata\\s*play|tata\\s*sky|dish\\s*tv|sun\\s*direct)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getId() {
        return "RULE_BILLS_UTILITIES";
    }

    @Override
    public int getOrder() {
        return 70;
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
        if (merchant != null && BILLS_MERCHANTS.contains(merchant.toLowerCase())) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.BILLS,
                    "Matched utility provider: " + merchant
            ));
        }

        // 2. Power utilities
        if (matchesPattern(POWER_DISCOM_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.BILLS,
                    "Matched electricity/power utility provider"
            ));
        }

        // 3. Gas & water utilities
        if (matchesPattern(GAS_WATER_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.BILLS,
                    "Matched piped gas/LPG/water utility provider"
            ));
        }

        // 4. Utility keyword pattern in composite or description
        if (matchesPattern(UTILITY_KEYWORDS_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.BILLS,
                    "Matched recurring utility/bill payment keywords"
            ));
        }

        // 5. Telecom / Internet providers (filter out payments bank accounts / P2P transfers)
        if (matchesPattern(TELECOM_PATTERN, merchant, composite, rawDesc, rawMerchant)) {
            if (isBankingTransferContext(composite, rawDesc, rawMerchant)) {
                return Optional.empty(); // Do not classify banking transfer as bill
            }
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.BILLS,
                    "Matched telecom / internet / DTH utility service"
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

    private boolean isBankingTransferContext(String composite, String rawDesc, String rawMerchant) {
        String combined = ((composite != null ? composite : "") + " "
                + (rawDesc != null ? rawDesc : "") + " "
                + (rawMerchant != null ? rawMerchant : "")).toLowerCase();
        return combined.contains("payments bank") || combined.contains("payment bank")
                || combined.contains("saving account") || combined.contains("savings account")
                || combined.contains("current account");
    }
}
