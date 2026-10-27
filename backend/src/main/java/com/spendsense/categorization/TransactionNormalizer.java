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
 * reproducible merchant keys, user-facing display names, and composite text for rules and ML.
 */
@Component
public class TransactionNormalizer {

    // Matches Indian bank transfer & rail prefixes (e.g., WDL TFR UPI/DR/, DEP TFR UPI/CR/, POS WDL-, NEFT-CR-, IMPS/P2A/, etc.)
    private static final Pattern PAYMENT_PREFIX_PATTERN = Pattern.compile(
            "^(?:(?:WDL|DEP|BIL|POS|ATM|ACH|INFT|FT|CHQ|CMS|ECS|TRF)[/_\\-\\s]+(?:TFR|WDL|DEP|TXN|CHG|CR|DR|TRF)?[/_\\-\\s]*)?" +
            "(?:UPI|POS|NEFT|IMPS|RTGS|ACH|ATM|NACH|INFT|FT|CARD)" +
            "(?:[/_\\-\\s]+(?:DR|CR|P2A|P2U|WDL|DEP|CMS))?[/_\\-\\s]+",
            Pattern.CASE_INSENSITIVE
    );

    // Matches UPI VPA handles (e.g. @okhdfcbank, @paytm, @ybl)
    private static final Pattern VPA_HANDLE_PATTERN = Pattern.compile("@[a-zA-Z0-9.\\-_]+");

    // Matches trailing bank branch / location markers (e.g. " AT 03117 VERSOVA (MUMBAI)")
    private static final Pattern BRANCH_LOCATION_PATTERN = Pattern.compile(
            "(?i)\\s+(?:AT|BR|BRANCH)\\s+\\d+.*$"
    );

    // Matches punctuation and delimiters to replace with spaces
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[*\\/\\\\_\\-:#~|,;]+");

    // Strips residual unwanted characters while preserving alphanumeric, ampersand, and apostrophe
    private static final Pattern CLEAN_CHARS_PATTERN = Pattern.compile("[^a-z0-9&'\\s]");

    // Standalone banking noise tokens
    private static final Set<String> NOISE_TAGS = Set.of(
            "ref", "refno", "txnid", "txn", "rrn", "utr", "dr", "cr"
    );

    // Common Indian bank IFSC codes / acronyms to filter when isolating merchant names
    private static final Set<String> KNOWN_BANK_CODES = Set.of(
            "kkbk", "hdfc", "sbin", "sbi", "icic", "icici", "yesb", "utib", "axis", "airp",
            "kotak", "idfc", "idfcbank", "punb", "pnb", "barb", "bob", "cnrb", "canara",
            "ut ib", "hd fc", "kk bk", "ye sb", "ic ic",
            "pytm", "paytm", "cbin", "srcb", "ubin", "ppiw"
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

        // If rawMerchant is empty or is an unparsed bank narration, try to extract the structured counterparty
        String targetMerchant = cleanRawMerchant;
        if (targetMerchant.isEmpty() || isRawBankNarration(targetMerchant)) {
            String candidateToParse = !targetMerchant.isEmpty() ? targetMerchant : cleanRawDescription;
            String extracted = extractMerchantFromNarration(candidateToParse);
            if (extracted != null && !extracted.isBlank() && !"unknown".equalsIgnoreCase(extracted)) {
                targetMerchant = extracted;
            } else if (targetMerchant.isEmpty()) {
                targetMerchant = "";
            }
        }

        if (targetMerchant.isEmpty()) {
            String normDesc = cleanRawDescription.isEmpty() ? "" : normalizeText(cleanRawDescription);
            String composite = normDesc.isEmpty() ? "unknown" : normDesc;
            return new NormalizedTransaction(
                    cleanRawMerchant,
                    cleanRawDescription,
                    "unknown",
                    normDesc,
                    composite,
                    List.of()
            );
        }

        // 1. Clean merchant text
        List<String> merchantTokens = cleanAndTokenize(targetMerchant, true);

        if (merchantTokens.isEmpty()) {
            // Fallback for purely unparseable inputs: keep sanitized raw string
            String fallback = targetMerchant.toLowerCase(Locale.ROOT);
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

        // 4. Build composite text for ML feature extraction
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

    /**
     * Extracts a human-friendly display merchant name suitable for UI presentation.
     * Never leaks raw bank narration (e.g. 'WDL TFR UPI/DR/6255...').
     * Returns "Unknown merchant" when extraction cannot safely identify a counterparty.
     */
    public String deriveDisplayMerchant(String rawMerchant, String rawDescription) {
        String merchantCandidate = rawMerchant != null ? rawMerchant.trim() : "";
        String descCandidate = rawDescription != null ? rawDescription.trim() : "";

        // If rawMerchant is already a clean merchant name (not raw bank metadata)
        if (!merchantCandidate.isEmpty() && !isRawBankNarration(merchantCandidate)) {
            String cleaned = cleanMerchantForDisplay(merchantCandidate);
            if (isValidMerchantDisplay(cleaned)) {
                return cleaned;
            }
        }

        // Try extracting structured merchant from rawMerchant narration
        if (!merchantCandidate.isEmpty()) {
            String extracted = extractMerchantFromNarration(merchantCandidate);
            if (isValidMerchantDisplay(extracted)) {
                return cleanMerchantForDisplay(extracted);
            }
        }

        // Try extracting structured merchant from rawDescription narration
        if (!descCandidate.isEmpty()) {
            String extracted = extractMerchantFromNarration(descCandidate);
            if (isValidMerchantDisplay(extracted)) {
                return cleanMerchantForDisplay(extracted);
            }
        }

        return "Unknown merchant";
    }

    /**
     * Convenience overload for deriving display merchant from a single string.
     */
    public String deriveDisplayMerchant(String rawText) {
        return deriveDisplayMerchant(rawText, null);
    }

    /**
     * Detects whether a string is raw banking metadata rather than a clean merchant.
     */
    public boolean isRawBankNarration(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        return upper.contains("UPI/")
                || upper.startsWith("UPI-")
                || upper.contains("WDL TFR")
                || upper.contains("DEP TFR")
                || upper.contains("BIL WDL")
                || upper.startsWith("NEFT-")
                || upper.startsWith("IMPS/")
                || upper.startsWith("IMPS-")
                || upper.startsWith("POS-")
                || upper.startsWith("POS/")
                || upper.startsWith("ATM-")
                || upper.startsWith("ACH/")
                || upper.contains("/DR/")
                || upper.contains("/CR/");
    }

    /**
     * Parses structured Indian bank narrations across UPI, IMPS, NEFT, and POS rails.
     */
    public String extractMerchantFromNarration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String cleaned = raw.trim();

        // 1. Strip trailing branch / location metadata (e.g. " AT 03117 VERSOVA (MUMBAI)")
        cleaned = BRANCH_LOCATION_PATTERN.matcher(cleaned).replaceFirst("").trim();

        // 2. Structured Slash-Separated UPI Narrations (Kotak, HDFC, SBI, ICICI, etc.)
        // e.g., WDL TFR UPI/DR/625481840735/Rapido/UT IB/rapido5223/UPI 0097694162092
        // e.g., DEP TFR UPI/CR/129594530836/LEENA AS/HDFC/imleenason/UPI 0097732162091
        // e.g., UPI/DR/626204506930/Blinkit/HD FC/blinkit.pa/UPIIn 0097695162091
        if (cleaned.contains("/")) {
            String[] segments = cleaned.split("/+");
            List<String> validSegments = new ArrayList<>();

            for (String seg : segments) {
                String s = seg.trim();
                if (isIgnorableNarrationSegment(s)) {
                    continue;
                }
                validSegments.add(s);
            }

            if (!validSegments.isEmpty()) {
                String candidate = validSegments.get(0);
                if (isValidMerchantDisplay(candidate)) {
                    return candidate;
                }
            }
        }

        // 3. Hyphen-Separated Narrations (e.g., UPI-HP Petrol Pump-..., NEFT-CR-HDFC000123-SWIGGY-SALARY)
        String upper = cleaned.toUpperCase(Locale.ROOT);
        if (upper.startsWith("UPI-") || upper.startsWith("NEFT-") || upper.startsWith("POS-") || upper.startsWith("IMPS-")) {
            String[] hyphenParts = cleaned.split("-+");
            for (String part : hyphenParts) {
                String p = part.trim();
                if (!isIgnorableNarrationSegment(p) && isValidMerchantDisplay(p)) {
                    return p;
                }
            }
        }

        return null;
    }

    private boolean isIgnorableNarrationSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return true;
        }
        String lower = segment.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();

        // Rail tags
        if (lower.startsWith("wdl tfr") || lower.startsWith("dep tfr") || lower.startsWith("bil wdl") || lower.startsWith("bil tfr")
                || lower.equals("upi") || lower.equals("dr") || lower.equals("cr")
                || lower.equals("p2a") || lower.equals("p2u")
                || lower.equals("wdl") || lower.equals("tfr") || lower.equals("dep")
                || lower.equals("pos") || lower.equals("neft") || lower.equals("imps")) {
            return true;
        }

        // Pure numeric RRNs, transaction IDs, phone numbers, or timestamps (length >= 5)
        if (segment.matches("^\\d{5,}$")) {
            return true;
        }

        // IFSC codes or alphanumeric bank IDs (e.g., HDFC000123)
        if (segment.matches("^[a-zA-Z]{4}\\d{6,}$") || segment.matches("^[a-zA-Z]{4}0[a-zA-Z0-9]{6}$")) {
            return true;
        }

        // Trailing branch/location notes
        if (lower.startsWith("at ") || lower.startsWith("br ") || lower.startsWith("upi 009")
                || lower.startsWith("sent 009") || lower.startsWith("orde 009") || lower.startsWith("upiin 009")) {
            return true;
        }

        // Known bank codes
        if (KNOWN_BANK_CODES.contains(lower)) {
            return true;
        }

        // Known noise words
        if (NOISE_TAGS.contains(lower)) {
            return true;
        }

        // Handles with @ (VPA)
        if (segment.contains("@")) {
            return true;
        }

        // Banking boilerplate words
        if (lower.equals("payment") || lower.equals("transfer") || lower.equals("sent") || lower.equals("orde")
                || lower.equals("upiin") || lower.equals("upiref")) {
            return true;
        }

        return false;
    }

    private boolean isValidMerchantDisplay(String s) {
        if (s == null || s.isBlank() || s.length() < 2) {
            return false;
        }
        if (isRawBankNarration(s)) {
            return false;
        }
        String lower = s.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("unknown") || lower.equals("unknown merchant")
                || lower.equals("payment") || lower.equals("transfer") || lower.equals("sent") || lower.equals("orde")
                || lower.equals("upiin") || lower.equals("upiref") || lower.equals("wdl") || lower.equals("tfr")
                || lower.equals("dep") || lower.equals("bil")) {
            return false;
        }
        if (NOISE_TAGS.contains(lower) || KNOWN_BANK_CODES.contains(lower)) {
            return false;
        }
        if (s.matches("^\\d+$")) {
            return false;
        }
        if (s.matches("^[a-zA-Z]{4}\\d{6,}$") || s.matches("^[a-zA-Z]{4}0[a-zA-Z0-9]{6}$")) {
            return false;
        }
        // Must contain at least one letter
        return s.matches(".*[a-zA-Z].*");
    }

    private String cleanMerchantForDisplay(String merchant) {
        if (merchant == null) return "Unknown merchant";
        String cleaned = merchant.trim();

        // Strip boilerplate suffixes like "PVT LTD", "LTD", "LIMITED", "LLP"
        cleaned = cleaned.replaceAll("(?i)\\s+(?:PVT\\.?\\s+LTD\\.?|LTD\\.?|LIMITED|LLP)$", "").trim();

        // Title case formatting if uppercase or lowercase
        if (cleaned.equals(cleaned.toUpperCase(Locale.ROOT)) || cleaned.equals(cleaned.toLowerCase(Locale.ROOT))) {
            cleaned = toTitleCase(cleaned);
        }

        return cleaned.isEmpty() ? "Unknown merchant" : cleaned;
    }

    private String toTitleCase(String input) {
        if (input == null || input.isBlank()) return "";
        String[] words = input.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1));
            }
        }
        return sb.toString();
    }

    private List<String> cleanAndTokenize(String input, boolean isMerchant) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        String cleaned = input.trim();

        // Strip branch location suffix
        cleaned = BRANCH_LOCATION_PATTERN.matcher(cleaned).replaceFirst("");

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
        // Known standalone noise words (utr, rrn, txn, wdl, tfr, etc.)
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
        // e.g. txn1234, ref837492, order12345, hdfc000123
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
