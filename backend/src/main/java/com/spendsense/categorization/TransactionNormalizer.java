package com.spendsense.categorization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic transaction normalization service.
 * Converts messy transaction strings and banking narrations into clean,
 * reproducible merchant keys and composite text for rules, user memory, and ML.
 */
@Component
public class TransactionNormalizer {

    // Matches payment rail prefixes (e.g. UPI/DR/, POS-, NEFT-CR-, IMPS/P2A/, ATM-WDL-)
    // Strictly matches payment rail tag and optional direction without consuming merchant characters
    private static final Pattern PAYMENT_PREFIX_PATTERN = Pattern.compile(
            "^(?:UPI|POS|NEFT|IMPS|RTGS|ACH|ATM)(?:[/_\\-\\s]+(?:DR|CR|P2A|P2U|WDL))?[/_\\-\\s]+",
            Pattern.CASE_INSENSITIVE
    );

    // Matches UPI VPA handles (e.g. @okhdfcbank, @paytm, @ybl)
    private static final Pattern VPA_HANDLE_PATTERN = Pattern.compile("@[a-zA-Z0-9.\\-_]+");

    // Matches punctuation and delimiters to replace with spaces
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[*\\/\\\\_\\-:#~|,;]+");

    // Strips residual unwanted characters while preserving alphanumeric, ampersand, and apostrophe
    private static final Pattern CLEAN_CHARS_PATTERN = Pattern.compile("[^a-z0-9&'\\s]");

    // Standalone banking noise tokens
    private static final Set<String> NOISE_TAGS = Set.of(
            "ref", "refno", "txnid", "txn", "rrn", "utr", "dr", "cr"
    );

    // Trailing boilerplate suffixes removed ONLY for canonical merchant key extraction
    // (e.g. "swiggy order" -> "swiggy", "swiggy in" -> "swiggy", "zomato online order" -> "zomato")
    // Meaningful category/product words ("prime", "india", "trip", "eats") are NEVER in this set.
    private static final Set<String> BOILERPLATE_MERCHANT_SUFFIXES = Set.of(
            "in", "order", "orders", "online", "pay", "pvt", "ltd", "limited", "corp", "inc", "llp"
    );

    /**
     * Normalizes both merchant and description into a comprehensive domain representation.
     */
    public NormalizedTransaction normalize(String rawMerchant, String rawDescription) {
        String cleanRawMerchant = rawMerchant != null ? rawMerchant.trim() : "";
        String cleanRawDescription = rawDescription != null ? rawDescription.trim() : "";

        if (cleanRawMerchant.isEmpty()) {
            return new NormalizedTransaction(
                    cleanRawMerchant,
                    cleanRawDescription,
                    "unknown",
                    cleanRawDescription.isEmpty() ? "" : normalizeText(cleanRawDescription),
                    cleanRawDescription.isEmpty() ? "unknown" : normalizeText(cleanRawDescription),
                    List.of()
            );
        }

        // 1. Clean merchant text
        List<String> merchantTokens = cleanAndTokenize(cleanRawMerchant, true);

        if (merchantTokens.isEmpty()) {
            // Fallback for purely unparseable inputs: keep sanitized raw string
            String fallback = cleanRawMerchant.toLowerCase(Locale.ROOT);
            return new NormalizedTransaction(
                    cleanRawMerchant,
                    cleanRawDescription,
                    fallback,
                    cleanRawDescription.isEmpty() ? "" : normalizeText(cleanRawDescription),
                    fallback,
                    List.of(fallback)
            );
        }

        // 2. Derive stable merchant key for rules and user memory
        String normalizedMerchant = deriveNormalizedMerchant(merchantTokens);

        // 3. Clean description if provided
        List<String> descriptionTokens = cleanAndTokenize(cleanRawDescription, false);
        String normalizedDescription = String.join(" ", descriptionTokens);

        // 4. Build composite text for future ML feature extraction
        List<String> allTokens = new ArrayList<>(merchantTokens);
        allTokens.addAll(descriptionTokens);
        String compositeText = String.join(" ", allTokens);

        return new NormalizedTransaction(
                cleanRawMerchant,
                cleanRawDescription,
                normalizedMerchant,
                normalizedDescription,
                compositeText,
                merchantTokens
        );
    }

    /**
     * Convenience method returning the stable canonical merchant identifier.
     */
    public String normalizeMerchant(String rawMerchant) {
        if (rawMerchant == null || rawMerchant.isBlank()) {
            return "unknown";
        }
        return normalize(rawMerchant, null).normalizedMerchant();
    }

    /**
     * Convenience method returning cleaned, noise-free text.
     */
    public String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> tokens = cleanAndTokenize(text, false);
        return String.join(" ", tokens);
    }

    private List<String> cleanAndTokenize(String input, boolean isMerchant) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        String cleaned = input.trim();

        // Strip payment rail prefix if applicable (e.g. UPI/DR/, POS-, NEFT-CR-)
        if (isMerchant) {
            cleaned = PAYMENT_PREFIX_PATTERN.matcher(cleaned).replaceFirst("");
        }

        // Strip VPA handles (e.g. @okhdfcbank)
        cleaned = VPA_HANDLE_PATTERN.matcher(cleaned).replaceAll(" ");

        // Convert separators to space
        cleaned = SEPARATOR_PATTERN.matcher(cleaned).replaceAll(" ");

        // Lowercase and strip residual invalid symbols
        cleaned = cleaned.toLowerCase(Locale.ROOT);
        cleaned = CLEAN_CHARS_PATTERN.matcher(cleaned).replaceAll(" ");

        String[] rawTokens = cleaned.split("\\s+");
        List<String> validTokens = new ArrayList<>(rawTokens.length);

        for (String token : rawTokens) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (!isNoiseToken(token)) {
                validTokens.add(token);
            }
        }

        return validTokens;
    }

    /**
     * Determines whether a token is noise (transaction ID, reference number, or boilerplate tag).
     */
    private boolean isNoiseToken(String token) {
        // Known standalone noise words (utr, rrn, txn, etc.)
        if (NOISE_TAGS.contains(token)) {
            return true;
        }

        // Pure numeric strings:
        // Length >= 3 is considered transaction/order/timestamp noise (e.g. 12345, 834723, 123456789)
        // Length <= 2 is preserved (e.g. "7" in "7 eleven", "24" in "24 seven")
        if (token.matches("^\\d{3,}$")) {
            return true;
        }

        // Mixed alphanumeric tokens with length >= 5 containing both letters and digits:
        // e.g. txn1234, ref837492, order12345
        // Tokens with length < 5 (e.g. "3m", "7up", "4g", "b2b") are preserved as meaningful brands
        if (token.length() >= 5 && token.matches("^(?=.*[a-z])(?=.*\\d)[a-z\\d]+$")) {
            return true;
        }

        return false;
    }

    /**
     * Derives a stable canonical merchant representation for memory and rule matching.
     * Strips trailing transactional boilerplate suffixes (like "order", "online", or "in")
     * only when at least one core merchant token remains.
     */
    private String deriveNormalizedMerchant(List<String> tokens) {
        if (tokens.isEmpty()) {
            return "unknown";
        }
        if (tokens.size() == 1) {
            return tokens.get(0);
        }

        // Copy tokens and check for trailing boilerplate suffix
        List<String> merchantTokens = new ArrayList<>(tokens);
        while (merchantTokens.size() > 1) {
            String last = merchantTokens.get(merchantTokens.size() - 1);
            if (BOILERPLATE_MERCHANT_SUFFIXES.contains(last)) {
                merchantTokens.remove(merchantTokens.size() - 1);
            } else {
                break;
            }
        }

        return String.join(" ", merchantTokens);
    }
}
