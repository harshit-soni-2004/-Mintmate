package com.spendsense.ingestion.transfer;

import com.spendsense.account.FinancialAccount;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferDirection;
import com.spendsense.transaction.TransferMatchStatus;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * General UTR-based historical transfer reconciliation service.
 * Replaces hardcoded one-off migration patches (like the V12 amount filter) with
 * deterministic 12-digit UTR/RRN matching across distinct accounts for the same user.
 */
@Service
public class TransferReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(TransferReconciliationService.class);
    private static final int MAX_DATE_WINDOW_DAYS = 3;

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TransferMatchingService transferMatchingService;

    public TransferReconciliationService(
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            TransferMatchingService transferMatchingService
    ) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.transferMatchingService = transferMatchingService;
    }

    public record ProposedTransferMatch(
            UUID debitTransactionId,
            LocalDate debitDate,
            String debitAccountName,
            String debitMerchant,
            String debitUtr,
            UUID creditTransactionId,
            LocalDate creditDate,
            String creditAccountName,
            String creditMerchant,
            String creditUtr,
            BigDecimal amount,
            String matchEvidence
    ) {}

    public record TransferReconciliationReport(
            String userEmail,
            int totalInspected,
            int proposedMatchesCount,
            List<ProposedTransferMatch> proposedMatches
    ) {}

    /**
     * Executes a dry-run reconciliation for a user.
     * Guaranteed to perform zero database mutations.
     */
    @Transactional(readOnly = true)
    public TransferReconciliationReport runDryRunForUser(UUID userId) {
        return reconcileUser(userId, true);
    }

    /**
     * Executes reconciliation by email in dry-run mode.
     */
    @Transactional(readOnly = true)
    public TransferReconciliationReport runDryRunForUserEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
        return reconcileUser(user.getId(), true);
    }

    /**
     * Reconciles transfer pairs for a user.
     * When dryRun is true, no changes are committed to the database.
     */
    @Transactional
    public TransferReconciliationReport reconcileUser(UUID userId, boolean dryRun) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<Transaction> allUserTransactions = transactionRepository.findByUser_IdOrderByTransactionDateDesc(userId);

        List<Transaction> unlinkedDebits = allUserTransactions.stream()
                .filter(t -> t.getLinkedTransaction() == null)
                .filter(t -> t.getTransactionType() == TransactionType.EXPENSE
                        || (t.getTransactionType() == TransactionType.TRANSFER
                        && (t.getTransferDirection() == TransferDirection.OUT || t.getTransferDirection() == null)))
                .toList();

        List<Transaction> unlinkedCredits = allUserTransactions.stream()
                .filter(t -> t.getLinkedTransaction() == null)
                .filter(t -> t.getTransactionType() == TransactionType.INCOME
                        || (t.getTransactionType() == TransactionType.TRANSFER
                        && (t.getTransferDirection() == TransferDirection.IN || t.getTransferDirection() == null)))
                .toList();

        List<ProposedTransferMatch> proposed = new ArrayList<>();
        Set<UUID> matchedCreditIds = new HashSet<>();
        Set<UUID> matchedDebitIds = new HashSet<>();

        for (Transaction debit : unlinkedDebits) {
            if (matchedDebitIds.contains(debit.getId())) continue;

            String debitUtr = transferMatchingService.extractUtr(debit);
            if (debitUtr == null || debitUtr.isBlank()) continue;

            for (Transaction credit : unlinkedCredits) {
                if (matchedCreditIds.contains(credit.getId())) continue;
                if (debit.getId().equals(credit.getId())) continue;

                // 1. Must be distinct accounts of the same user
                if (!transferMatchingService.isDifferentAccount(debit, credit)) {
                    continue;
                }

                // 2. Exact amount match
                if (debit.getAmount() == null || credit.getAmount() == null
                        || debit.getAmount().compareTo(credit.getAmount()) != 0) {
                    continue;
                }

                // 3. Date window <= 3 days
                if (debit.getTransactionDate() == null || credit.getTransactionDate() == null
                        || Math.abs(ChronoUnit.DAYS.between(debit.getTransactionDate(), credit.getTransactionDate())) > MAX_DATE_WINDOW_DAYS) {
                    continue;
                }

                // 4. Deterministic UTR match
                String creditUtr = transferMatchingService.extractUtr(credit);
                if (creditUtr == null || !debitUtr.equals(creditUtr)) {
                    continue;
                }

                // Found matching pair!
                String debitAcc = debit.getAccount() != null ? debit.getAccount().getName() : "Unknown";
                String creditAcc = credit.getAccount() != null ? credit.getAccount().getName() : "Unknown";

                proposed.add(new ProposedTransferMatch(
                        debit.getId(),
                        debit.getTransactionDate(),
                        debitAcc,
                        debit.getMerchant(),
                        debitUtr,
                        credit.getId(),
                        credit.getTransactionDate(),
                        creditAcc,
                        credit.getMerchant(),
                        creditUtr,
                        debit.getAmount(),
                        String.format("Identical 12-digit UTR '%s' across distinct accounts (%s -> %s)",
                                debitUtr, debitAcc, creditAcc)
                ));

                matchedDebitIds.add(debit.getId());
                matchedCreditIds.add(credit.getId());

                if (!dryRun) {
                    // Apply reciprocal link
                    debit.setTransactionType(TransactionType.TRANSFER);
                    debit.setTransferDirection(TransferDirection.OUT);
                    debit.setTransferMatchStatus(TransferMatchStatus.AUTO_MATCHED);
                    debit.setCategory(TransactionCategory.OTHER);
                    debit.linkTransfer(credit, credit.getAccount(), TransferMatchStatus.AUTO_MATCHED);

                    credit.setTransactionType(TransactionType.TRANSFER);
                    credit.setTransferDirection(TransferDirection.IN);
                    credit.setTransferMatchStatus(TransferMatchStatus.AUTO_MATCHED);
                    credit.setCategory(TransactionCategory.OTHER);
                    credit.linkTransfer(debit, debit.getAccount(), TransferMatchStatus.AUTO_MATCHED);

                    transactionRepository.save(debit);
                    transactionRepository.save(credit);

                    log.info("Applied transfer reconciliation match: debit {} <-> credit {}, amount: {}, UTR: {}",
                            debit.getId(), credit.getId(), debit.getAmount(), debitUtr);
                }
                break;
            }
        }

        return new TransferReconciliationReport(
                user.getEmail(),
                allUserTransactions.size(),
                proposed.size(),
                proposed
        );
    }
}
