package com.spendsense.categorization.rule;

import com.spendsense.transaction.TransactionCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic rule mapping ride-hailing, public transit, flights, fuel, and tolls to TRAVEL.
 * Carefully avoids ambiguous substrings (e.g. 'cola' or 'solar' matching 'ola', or 'uber eats' matching travel).
 */
@Component
public class RideHailingAndTravelRule implements CategorizationRule {

    private static final Set<String> TRAVEL_MERCHANTS = Set.of(
            "rapido", "rapidobike", "rapido auto", "rapido cab",
            "uber trip", "uber rides", "uber india", "uber bv",
            "ola cabs", "olacabs", "ola ride",
            "irctc", "indian railways", "railways", "railway",
            "indigo", "air india", "vistara", "spicejet", "akasa air",
            "redbus", "abhibus", "makemytrip", "goibibo", "yatra", "easemytrip",
            "hpcl", "bpcl", "iocl", "indian oil", "bharat petroleum", "hindustan petroleum",
            "shell petrol", "petrol pump", "fastag", "toll plaza",
            "bmrcl", "dmrc", "delhi metro", "namma metro", "mumbai metro", "metro rail"
    );

    // Exact word patterns for ambiguous names
    private static final Pattern OLA_PATTERN = Pattern.compile(
            "\\b(ola|ola\\s*cabs?|olacabs)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UBER_PATTERN = Pattern.compile(
            "\\b(uber|uber\\s*trip|uber\\s*india|uber\\s*rides?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAPIDO_PATTERN = Pattern.compile(
            "\\b(rapido|rapido\\s*bike|rapido\\s*auto|rapido\\s*cabs?)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern FUEL_PATTERN = Pattern.compile(
            "\\b(hpcl|bpcl|iocl|indian\\s*oil|bharat\\s*petrol|hindustan\\s*petrol|shell\\s*fuel|petrol\\s*pump|fuel\\s*station|fastag|toll\\s*plaza)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern METRO_PATTERN = Pattern.compile(
            "\\b(delhi\\s*metro|mumbai\\s*metro|namma\\s*metro|bmrcl|dmrc|metro\\s*rail|irctc|indian\\s*railways)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getId() {
        return "RULE_RIDE_HAILING_TRAVEL";
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

        String merchant = context.normalizedTransaction().normalizedMerchant();
        String composite = context.normalizedTransaction().compositeText();
        String rawDesc = context.normalizedTransaction().rawDescription();
        String rawMerchant = context.normalizedTransaction().rawMerchant();

        // 1. Check exact canonical merchant match
        if (merchant != null && TRAVEL_MERCHANTS.contains(merchant.toLowerCase())) {
            // Disambiguate Uber Eats -> should not be travel
            if (merchant.toLowerCase().contains("eats") || (composite != null && composite.contains("eats"))) {
                return Optional.empty();
            }
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.TRAVEL,
                    "Matched travel merchant: " + merchant
            ));
        }

        // 2. Disambiguate Uber (exclude Uber Eats / food delivery)
        if (matchesUber(merchant, composite, rawDesc, rawMerchant)) {
            if (isFoodDeliveryContext(composite, rawDesc)) {
                return Optional.empty(); // Let food rule handle Uber Eats
            }
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.TRAVEL,
                    "Matched Uber ride/travel service"
            ));
        }

        // 3. Disambiguate Ola (exclude Coca-Cola, Solar, Viola, Poland, and Ola Foods/Dash)
        if (matchesOla(merchant, composite, rawDesc, rawMerchant)) {
            if (isFoodDeliveryContext(composite, rawDesc)) {
                return Optional.empty(); // Let food rule handle Ola Foods
            }
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.TRAVEL,
                    "Matched Ola cab/travel service"
            ));
        }

        // 4. Disambiguate Rapido (exclude words like 'rapid' alone)
        if (matchesRapido(merchant, composite, rawDesc, rawMerchant)) {
            return Optional.of(RuleMatchResult.matched(
                    getId(),
                    TransactionCategory.TRAVEL,
                    "Matched Rapido bike/cab ride service"
            ));
        }

        // 5. Fuel & Transit
        if (composite != null && !composite.isBlank()) {
            if (FUEL_PATTERN.matcher(composite).find()) {
                return Optional.of(RuleMatchResult.matched(
                        getId(),
                        TransactionCategory.TRAVEL,
                        "Matched fuel/petrol/toll transit pattern"
                ));
            }
            if (METRO_PATTERN.matcher(composite).find()) {
                return Optional.of(RuleMatchResult.matched(
                        getId(),
                        TransactionCategory.TRAVEL,
                        "Matched public transit/metro/rail pattern"
                ));
            }
        }

        return Optional.empty();
    }

    private boolean matchesUber(String merchant, String composite, String rawDesc, String rawMerchant) {
        if ("uber".equalsIgnoreCase(merchant)) return true;
        if (merchant != null && UBER_PATTERN.matcher(merchant).find()) return true;
        if (composite != null && UBER_PATTERN.matcher(composite).find()) return true;
        if (rawDesc != null && UBER_PATTERN.matcher(rawDesc).find()) return true;
        if (rawMerchant != null && UBER_PATTERN.matcher(rawMerchant).find()) return true;
        return false;
    }

    private boolean matchesOla(String merchant, String composite, String rawDesc, String rawMerchant) {
        if ("ola".equalsIgnoreCase(merchant) || "ola cabs".equalsIgnoreCase(merchant) || "olacabs".equalsIgnoreCase(merchant)) {
            return true;
        }
        // Ensure no false matches like "cola", "solar", "viola", "poland"
        if (isAmbiguousOlaFalsePositive(composite) || isAmbiguousOlaFalsePositive(rawDesc)) {
            return false;
        }
        if (merchant != null && OLA_PATTERN.matcher(merchant).find() && !isAmbiguousOlaFalsePositive(merchant)) {
            return true;
        }
        if (composite != null && OLA_PATTERN.matcher(composite).find() && !isAmbiguousOlaFalsePositive(composite)) {
            return true;
        }
        if (rawDesc != null && OLA_PATTERN.matcher(rawDesc).find() && !isAmbiguousOlaFalsePositive(rawDesc)) {
            return true;
        }
        return false;
    }

    private boolean isAmbiguousOlaFalsePositive(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("cola") || lower.contains("solar") || lower.contains("viola") || lower.contains("poland") || lower.contains("cholamandal");
    }

    private boolean matchesRapido(String merchant, String composite, String rawDesc, String rawMerchant) {
        if ("rapido".equalsIgnoreCase(merchant)) return true;
        if (merchant != null && RAPIDO_PATTERN.matcher(merchant).find()) return true;
        if (composite != null && RAPIDO_PATTERN.matcher(composite).find()) return true;
        if (rawDesc != null && RAPIDO_PATTERN.matcher(rawDesc).find()) return true;
        if (rawMerchant != null && RAPIDO_PATTERN.matcher(rawMerchant).find()) return true;
        return false;
    }

    private boolean isFoodDeliveryContext(String composite, String rawDesc) {
        String combined = ((composite != null ? composite : "") + " " + (rawDesc != null ? rawDesc : "")).toLowerCase();
        return combined.contains("eats") || combined.contains("food") || combined.contains("dash") || combined.contains("grocery");
    }
}
