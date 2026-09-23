package com.spendsense.ingestion.transfer;

import com.spendsense.account.FinancialAccount;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferDirection;
import com.spendsense.transaction.TransferMatchStatus;
import com.spendsense.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic transfer matching engine.
 * Pairs opposing legs of internal transfers across user's financial accounts.
 */
@Service
public class TransferMatchingService {

    private static final Logger log = LoggerFactory.getLogger(TransferMatchingService.class);
    private static final int MAX_TRANSFER_DATE_WINDOW_DAYS = 3;
    private static final Pattern UTR_RRN_PATTERN = Pattern.compile("\\b([0-9]{12})\\b");

    public static final Pattern INTERNAL_TRANSFER_KEYWORDS = Pattern.compile(
            "\\b(OWN ACC|INTERNAL TRF|SELF TRF|TRF TO SELF|TRF FROM SELF|TRANSFER TO ACC|TRANSFER FROM ACC|SENT TO STATE BA|SENT TO KOTAK|SENT TO HDFC|SENT TO ICICI|SENT TO AXIS|SENT TO SBI)\\b",
            Pattern.CASE_INSENSITIVE);

    private final TransactionRepository transactionRepository;

    public TransferMatchingService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Evaluates a transfer transaction. If an opposing unmatched transfer leg exists for the same user,
     * deterministically pairs them up. Also checks for unlinked candidates in other accounts that can
     * be retroactively upgraded. If none found, marks as UNMATCHED without converting it to an expense.
     */
    @Transactional
    public boolean attemptMatch(Transaction newTransfer) {
        Objects.requireNonNull(newTransfer, "Transaction cannot be null");
        if (newTransfer.getTransactionType() != TransactionType.TRANSFER) {
            return false;
        }

        if (newTransfer.getTransferMatchStatus() == TransferMatchStatus.AUTO_MATCHED
                || newTransfer.getTransferMatchStatus() == TransferMatchStatus.MANUALLY_CONFIRMED
                || newTransfer.getLinkedTransaction() != null) {
            return true;
        }

        TransferDirection opposingDirection = newTransfer.getTransferDirection() == TransferDirection.OUT
                ? TransferDirection.IN
                : TransferDirection.OUT;

        // 1. Query unmatched transfers for the user with opposing direction and same amount
        List<Transaction> candidates = transactionRepository.findUnmatchedTransfers(
                newTransfer.getUser().getId(),
                TransactionType.TRANSFER,
                opposingDirection,
                TransferMatchStatus.UNMATCHED,
                newTransfer.getAmount()
        );

        Optional<Transaction> matchedCandidate = candidates.stream()
                .filter(c -> !c.getId().equals(newTransfer.getId()))
                .filter(c -> isDifferentAccount(c, newTransfer))
                .filter(c -> Math.abs(ChronoUnit.DAYS.between(c.getTransactionDate(), newTransfer.getTransactionDate())) <= MAX_TRANSFER_DATE_WINDOW_DAYS)
                .filter(c -> hasMatchingConfidence(newTransfer, c))
                .findFirst();

        if (matchedCandidate.isPresent()) {
            Transaction counterpart = matchedCandidate.get();
            newTransfer.linkTransfer(counterpart, counterpart.getAccount(), TransferMatchStatus.AUTO_MATCHED);
            counterpart.linkTransfer(newTransfer, newTransfer.getAccount(), TransferMatchStatus.AUTO_MATCHED);

            transactionRepository.save(newTransfer);
            transactionRepository.save(counterpart);

            log.info("Auto-matched internal transfer between transaction {} and {} for user {} amount {}",
                    newTransfer.getId(), counterpart.getId(), newTransfer.getUser().getId(), newTransfer.getAmount());
            return true;
        }

        // 2. Query potential unlinked candidates in other accounts for retroactive upgrade
        LocalDate minDate = newTransfer.getTransactionDate().minusDays(MAX_TRANSFER_DATE_WINDOW_DAYS);
        LocalDate maxDate = newTransfer.getTransactionDate().plusDays(MAX_TRANSFER_DATE_WINDOW_DAYS);
        List<Transaction> potentialCounterparts = transactionRepository.findPotentialTransferCounterparts(
                newTransfer.getUser().getId(),
                newTransfer.getAmount(),
                minDate,
                maxDate
        );

        Optional<Transaction> matchedPotential = potentialCounterparts.stream()
                .filter(c -> !c.getId().equals(newTransfer.getId()))
                .filter(c -> isDifferentAccount(c, newTransfer))
                .filter(c -> isOpposingType(newTransfer, c))
                .filter(c -> hasMatchingConfidence(newTransfer, c))
                .findFirst();

        if (matchedPotential.isPresent()) {
            return retroactivelyUpgradeAndMatch(newTransfer, matchedPotential.get());
        }

        newTransfer.setTransferMatchStatus(TransferMatchStatus.UNMATCHED);
        transactionRepository.save(newTransfer);
        log.info("Recorded unmatched transfer {} for user {} amount {}",
                newTransfer.getId(), newTransfer.getUser().getId(), newTransfer.getAmount());
        return false;
    }

    /**
     * Retroactively upgrades an existing transaction (stored as EXPENSE or INCOME) to TRANSFER
     * and reciprocally links both sides as AUTO_MATCHED.
     */
    @Transactional
    public boolean retroactivelyUpgradeAndMatch(Transaction newTx, Transaction existingOppositeTx) {
        if (newTx == null || existingOppositeTx == null) {
            return false;
        }
        if (newTx.getId() != null && newTx.getId().equals(existingOppositeTx.getId())) {
            return false;
        }
        if (!isDifferentAccount(newTx, existingOppositeTx)) {
            return false;
        }

        boolean isNewCredit = isCreditTransaction(newTx);
        TransferDirection newDirection = isNewCredit ? TransferDirection.IN : TransferDirection.OUT;
        TransferDirection existingDirection = isNewCredit ? TransferDirection.OUT : TransferDirection.IN;

        // Upgrade existing transaction
        existingOppositeTx.setTransactionType(TransactionType.TRANSFER);
        existingOppositeTx.setTransferDirection(existingDirection);
        existingOppositeTx.setCategory(TransactionCategory.OTHER);
        existingOppositeTx.linkTransfer(newTx, newTx.getAccount(), TransferMatchStatus.AUTO_MATCHED);

        // Update new transaction
        newTx.setTransactionType(TransactionType.TRANSFER);
        newTx.setTransferDirection(newDirection);
        newTx.setCategory(TransactionCategory.OTHER);
        newTx.linkTransfer(existingOppositeTx, existingOppositeTx.getAccount(), TransferMatchStatus.AUTO_MATCHED);

        transactionRepository.save(existingOppositeTx);
        transactionRepository.save(newTx);

        log.info("Retroactively upgraded and auto-matched transfer between tx {} ({}) and tx {} ({}) for user {} amount {}",
                newTx.getId(), newDirection, existingOppositeTx.getId(), existingDirection, newTx.getUser().getId(), newTx.getAmount());
        return true;
    }

    /**
     * Finds a bilateral counterpart among existing transactions for a newly extracted candidate.
     */
    public Optional<Transaction> findCounterpartForCandidate(
            TransactionCandidate candidate,
            boolean isCredit,
            FinancialAccount currentAccount,
            List<Transaction> existingTransactions,
            User user
    ) {
        if (candidate == null || candidate.amount() == null || existingTransactions == null || existingTransactions.isEmpty()) {
            return Optional.empty();
        }

        String candUtr = extractUtr(candidate);
        String candRef = candidate.externalReference();
        String candText = ((candidate.rawMerchant() != null ? candidate.rawMerchant() : "") + " "
                + (candidate.rawDescription() != null ? candidate.rawDescription() : "")).toLowerCase();

        return existingTransactions.stream()
                .filter(t -> t.getLinkedTransaction() == null)
                .filter(t -> t.getAmount() != null && t.getAmount().compareTo(candidate.amount()) == 0)
                .filter(t -> t.getTransactionDate() != null && candidate.transactionDate() != null
                        && Math.abs(ChronoUnit.DAYS.between(t.getTransactionDate(), candidate.transactionDate())) <= MAX_TRANSFER_DATE_WINDOW_DAYS)
                .filter(t -> currentAccount == null || t.getAccount() == null || !t.getAccount().getId().equals(currentAccount.getId()))
                .filter(t -> isOpposingDirection(isCredit, t))
                .filter(t -> hasCandidateMatchingConfidence(candUtr, candRef, candText, t, user))
                .findFirst();
    }

    /**
     * Unlinks counterpart transfer legs before deleting transactions.
     */
    @Transactional
    public void unlinkBeforeDeletion(List<Transaction> transactionsToDelete) {
        if (transactionsToDelete == null || transactionsToDelete.isEmpty()) {
            return;
        }

        for (Transaction tx : transactionsToDelete) {
            if (tx.getTransactionType() == TransactionType.TRANSFER && tx.getLinkedTransaction() != null) {
                Transaction counterpart = tx.getLinkedTransaction();
                boolean counterpartAlsoBeingDeleted = transactionsToDelete.stream()
                        .anyMatch(t -> t.getId().equals(counterpart.getId()));
                if (!counterpartAlsoBeingDeleted) {
                    counterpart.unlinkTransfer();
                    transactionRepository.save(counterpart);
                    log.info("Unlinked transfer counterpart {} because paired transaction {} is being deleted",
                            counterpart.getId(), tx.getId());
                }
            }
        }
    }

    public boolean isDifferentAccount(Transaction a, Transaction b) {
        if (a == null || b == null || a.getAccount() == null || b.getAccount() == null) {
            return false;
        }
        return !a.getAccount().getId().equals(b.getAccount().getId());
    }

    public boolean hasMatchingConfidence(Transaction a, Transaction b) {
        if (!isDifferentAccount(a, b)) {
            return false;
        }

        // High confidence signal 1: Matching 12-digit UTR/RRN
        String utrA = extractUtr(a);
        String utrB = extractUtr(b);
        if (utrA != null && utrA.equals(utrB)) {
            return true;
        }

        // High confidence signal 2: Matching clean source reference (min 6 chars, ignoring doc:...:row:... prefix)
        String refA = cleanReference(a.getSourceReference());
        String refB = cleanReference(b.getSourceReference());
        if (refA != null && refB != null && refA.length() >= 6 && refA.equalsIgnoreCase(refB)) {
            return true;
        }

        String textA = ((a.getMerchant() != null ? a.getMerchant() : "") + " " + (a.getDescription() != null ? a.getDescription() : "")).toLowerCase();
        String textB = ((b.getMerchant() != null ? b.getMerchant() : "") + " " + (b.getDescription() != null ? b.getDescription() : "")).toLowerCase();

        // High confidence signal 3: Cross-bank institution code/name + supporting name signal
        boolean crossBankMention = (a.getAccount() != null && containsInstitutionReference(textB, a.getAccount().getInstitutionName()))
                || (b.getAccount() != null && containsInstitutionReference(textA, b.getAccount().getInstitutionName()));

        boolean nameSignal = (a.getUser() != null && matchesUserNameSignal(a.getUser(), textA + " " + textB));

        if (crossBankMention && nameSignal) {
            return true;
        }

        // High confidence signal 4: Internal transfer keywords + supporting name signal
        boolean hasTransferKeywords = INTERNAL_TRANSFER_KEYWORDS.matcher(textA).find()
                || INTERNAL_TRANSFER_KEYWORDS.matcher(textB).find();
        if (hasTransferKeywords && nameSignal) {
            return true;
        }

        return false;
    }

    private boolean hasCandidateMatchingConfidence(
            String candUtr,
            String candRef,
            String candText,
            Transaction existingTx,
            User user
    ) {
        // High confidence signal 1: Matching 12-digit UTR/RRN
        String existingUtr = extractUtr(existingTx);
        if (candUtr != null && candUtr.equals(existingUtr)) {
            return true;
        }

        // High confidence signal 2: Matching dedicated reference (min 6 chars, ignoring doc:...:row:... prefix)
        String cleanExistingRef = cleanReference(existingTx.getSourceReference());
        if (candRef != null && !candRef.isBlank() && candRef.length() >= 6 && candRef.equalsIgnoreCase(cleanExistingRef)) {
            return true;
        }

        String existingText = ((existingTx.getMerchant() != null ? existingTx.getMerchant() : "") + " "
                + (existingTx.getDescription() != null ? existingTx.getDescription() : "")).toLowerCase();

        // High confidence signal 3: Cross-bank reference or institution name + name signal
        boolean mentionsExistingBank = existingTx.getAccount() != null
                && existingTx.getAccount().getInstitutionName() != null
                && containsInstitutionReference(candText, existingTx.getAccount().getInstitutionName());

        boolean userSignal = matchesUserNameSignal(user, candText) || matchesUserNameSignal(user, existingText);

        if (mentionsExistingBank && userSignal) {
            return true;
        }

        // High confidence signal 4: Internal transfer keywords + name signal
        boolean hasTransferKeywords = INTERNAL_TRANSFER_KEYWORDS.matcher(candText).find()
                || INTERNAL_TRANSFER_KEYWORDS.matcher(existingText).find();
        if (hasTransferKeywords && userSignal) {
            return true;
        }

        return false;
    }

    public static boolean containsInstitutionReference(String text, String institutionName) {
        if (text == null || institutionName == null || institutionName.isBlank()) {
            return false;
        }
        String inst = institutionName.toLowerCase().trim();
        if (inst.length() >= 3 && text.contains(inst)) {
            return true;
        }
        if (inst.contains("sbi") || inst.contains("state bank")) {
            if (text.contains("sbi") || text.contains("state ba") || text.contains("sbin")) {
                return true;
            }
        }
        if (inst.contains("kotak")) {
            if (text.contains("kotak") || text.contains("kkbk")) {
                return true;
            }
        }
        if (inst.contains("hdfc")) {
            if (text.contains("hdfc")) {
                return true;
            }
        }
        if (inst.contains("icici")) {
            if (text.contains("icici")) {
                return true;
            }
        }
        if (inst.contains("axis")) {
            if (text.contains("axis") || text.contains("utib")) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesUserNameSignal(User user, String text) {
        if (user == null || text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase();

        // 1. Display name tokens
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            String dn = user.getDisplayName().toLowerCase().trim();
            if (dn.length() >= 3 && normalized.contains(dn)) {
                return true;
            }
            String[] tokens = dn.split("[\\s._-]+");
            for (String token : tokens) {
                if (token.length() >= 3 && normalized.contains(token)) {
                    return true;
                }
            }
        }

        // 2. Email username prefix
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            String email = user.getEmail().toLowerCase().trim();
            int atIdx = email.indexOf('@');
            if (atIdx > 2) {
                String prefix = email.substring(0, atIdx);
                if (prefix.length() >= 3 && normalized.contains(prefix)) {
                    return true;
                }
                String[] tokens = prefix.split("[._-]+");
                for (String token : tokens) {
                    if (token.length() >= 3 && normalized.contains(token)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static String cleanReference(String ref) {
        if (ref == null) return null;
        return ref.replaceFirst("^doc:[^:]+:row:\\d+:", "").trim();
    }

    public static String extractUtr(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = UTR_RRN_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public String extractUtr(Transaction tx) {
        if (tx == null) return null;
        // Priority 1: Extract genuine bank UTR from merchant and description
        String text = (tx.getMerchant() != null ? tx.getMerchant() : "")
                + " " + (tx.getDescription() != null ? tx.getDescription() : "");
        String utr = extractUtr(text);
        if (utr != null) {
            return utr;
        }
        // Priority 2: Fallback to source reference after stripping synthetic prefix
        String cleanRef = cleanReference(tx.getSourceReference());
        return extractUtr(cleanRef);
    }

    public String extractUtr(TransactionCandidate candidate) {
        if (candidate == null) return null;
        // Priority 1: Extract genuine bank UTR from raw merchant and raw description
        String text = (candidate.rawMerchant() != null ? candidate.rawMerchant() : "")
                + " " + (candidate.rawDescription() != null ? candidate.rawDescription() : "");
        String utr = extractUtr(text);
        if (utr != null) {
            return utr;
        }
        // Priority 2: Fallback to external reference
        return extractUtr(candidate.externalReference());
    }

    private boolean isCreditTransaction(Transaction tx) {
        if (tx.getTransferDirection() == TransferDirection.IN) {
            return true;
        }
        return tx.getTransactionType() == TransactionType.INCOME;
    }

    private boolean isOpposingDirection(boolean isCandidateCredit, Transaction existingTx) {
        if (isCandidateCredit) {
            return existingTx.getTransactionType() == TransactionType.EXPENSE
                    || existingTx.getTransferDirection() == TransferDirection.OUT;
        } else {
            return existingTx.getTransactionType() == TransactionType.INCOME
                    || existingTx.getTransferDirection() == TransferDirection.IN;
        }
    }

    private boolean isOpposingType(Transaction newTx, Transaction existingTx) {
        boolean isNewCredit = isCreditTransaction(newTx);
        return isOpposingDirection(isNewCredit, existingTx);
    }
}

