package com.spendsense.ingestion.dedup;

import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.Transaction;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic duplicate detection engine for statement ingestion.
 * Evaluates candidates against:
 * 1. Same-document duplicate rows (repeated sections/pages)
 * 2. Exact duplicates in user's existing transactions
 * 3. Probable duplicates (close date, same amount, similar merchant) requiring review
 */
@Service
public class DeduplicationService {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final int PROBABLE_DATE_WINDOW_DAYS = 2;

    /**
     * Checks if a candidate is a duplicate within the document batch or against existing persisted transactions.
     */
    public DuplicateCheckResult checkDuplicate(
            TransactionCandidate candidate,
            List<Transaction> existingTransactions,
            Set<String> seenDocumentSignatures
    ) {
        // 1. Same-document batch duplicate detection
        String signature = computeCandidateSignature(candidate);
        if (seenDocumentSignatures.contains(signature)) {
            return DuplicateCheckResult.exact(null, "Duplicate row within the same statement document");
        }
        seenDocumentSignatures.add(signature);

        if (existingTransactions == null || existingTransactions.isEmpty()) {
            return DuplicateCheckResult.unique();
        }

        String normCandidateMerchant = normalizeText(candidate.rawMerchant());
        String normCandidateDesc = normalizeText(candidate.rawDescription());
        String normCandidateCombined = normalizeText((candidate.rawMerchant() != null ? candidate.rawMerchant() : "")
                + " " + (candidate.rawDescription() != null ? candidate.rawDescription() : ""));

        // 2. Exact duplicate check
        for (Transaction existing : existingTransactions) {
            if (candidate.amount().compareTo(existing.getAmount()) == 0
                    && candidate.transactionDate().equals(existing.getTransactionDate())
                    && candidate.direction() == existing.getTransactionType()) {

                // Condition A: Same non-null external reference
                if (candidate.externalReference() != null && !candidate.externalReference().isBlank()) {
                    if (matchesReference(candidate.externalReference(), existing)) {
                        return DuplicateCheckResult.exact(existing, "Exact duplicate: matching date, amount, direction, and external reference");
                    }
                }

                // Condition B: Same normalized merchant or description
                String normExistingMerchant = normalizeText(existing.getMerchant());
                String normExistingDesc = normalizeText(existing.getDescription());

                if ((!normCandidateMerchant.isEmpty() && normCandidateMerchant.equals(normExistingMerchant))
                        || (!normCandidateDesc.isEmpty() && normCandidateDesc.equals(normExistingDesc))) {
                    return DuplicateCheckResult.exact(existing, "Exact duplicate: matching date, amount, direction, and normalized merchant/description");
                }

                // Condition C: Same originating statement row reference
                if (candidate.documentId() != null && existing.getSourceDocument() != null
                        && candidate.documentId().equals(existing.getSourceDocument().getId())
                        && existing.getSourceReference() != null
                        && existing.getSourceReference().startsWith("doc:" + candidate.documentId() + ":row:" + candidate.rowIndex())) {
                    return DuplicateCheckResult.exact(existing, "Exact duplicate: row already imported from this statement");
                }
            }
        }

        // 3. Probable duplicate check (do NOT silently discard, flagged for review)
        for (Transaction existing : existingTransactions) {
            if (candidate.amount().compareTo(existing.getAmount()) == 0
                    && candidate.direction() == existing.getTransactionType()) {

                long daysDiff = Math.abs(ChronoUnit.DAYS.between(candidate.transactionDate(), existing.getTransactionDate()));
                if (daysDiff <= PROBABLE_DATE_WINDOW_DAYS) {
                    String normExistingMerchant = normalizeText(existing.getMerchant());
                    String normExistingCombined = normalizeText(existing.getMerchant() + " " + (existing.getDescription() != null ? existing.getDescription() : ""));
                    if (isMerchantSimilar(normCandidateMerchant, normExistingMerchant)
                            || isMerchantSimilar(normCandidateCombined, normExistingCombined)) {
                        return DuplicateCheckResult.probable(existing,
                                String.format("Probable duplicate of existing transaction on %s for %s (%s)",
                                        existing.getTransactionDate(), existing.getAmount(), existing.getMerchant()));
                    }
                }
            }
        }

        return DuplicateCheckResult.unique();
    }

    private boolean matchesReference(String reference, Transaction existing) {
        String ref = reference.trim();
        if (existing.getSourceReference() != null && existing.getSourceReference().contains(ref)) {
            return true;
        }
        if (existing.getNotes() != null && existing.getNotes().contains(ref)) {
            return true;
        }
        return false;
    }

    private String computeCandidateSignature(TransactionCandidate candidate) {
        String ref = candidate.externalReference() != null ? candidate.externalReference().trim().toLowerCase(Locale.ROOT) : "";
        String text = ref.isEmpty()
                ? normalizeText((candidate.rawMerchant() != null ? candidate.rawMerchant() : "") + " " + (candidate.rawDescription() != null ? candidate.rawDescription() : ""))
                : ref;
        return candidate.transactionDate() + "|" + candidate.amount().toPlainString() + "|" + candidate.direction() + "|" + text;
    }

    public static String normalizeText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String lower = input.toLowerCase(Locale.ROOT);
        String cleaned = NON_ALPHANUMERIC.matcher(lower).replaceAll(" ");
        return MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ").trim();
    }

    public static boolean isMerchantSimilar(String s1, String s2) {
        if (s1.isEmpty() || s2.isEmpty()) {
            return false;
        }
        if (s1.equals(s2)) {
            return true;
        }
        if ((s1.contains(s2) && s2.length() >= 4) || (s2.contains(s1) && s1.length() >= 4)) {
            return true;
        }

        Set<String> set1 = new HashSet<>(Arrays.asList(s1.split(" ")));
        Set<String> set2 = new HashSet<>(Arrays.asList(s2.split(" ")));

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        if (intersection.isEmpty()) {
            return false;
        }

        // Check if any shared token is a significant brand word (>= 4 chars, e.g. "uber", "swiggy", "zomato")
        boolean hasSignificantToken = intersection.stream().anyMatch(token -> token.length() >= 4);
        if (hasSignificantToken) {
            double overlapCoef = (double) intersection.size() / Math.min(set1.size(), set2.size());
            if (overlapCoef >= 0.3) {
                return true;
            }
        }

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        double jaccard = (double) intersection.size() / union.size();
        return jaccard >= 0.4;
    }
}
