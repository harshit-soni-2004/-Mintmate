package com.spendsense.ingestion.settlement;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.FinancialDocument;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Service for deterministic settlement, transfer, and refund resolution.
 * Matches bank-side credit card payments with card-side payments received,
 * handles internal account transfers, and stores refunds as offsets rather than income.
 */
@Service
public class SettlementResolutionService {

    private static final Logger log = LoggerFactory.getLogger(SettlementResolutionService.class);
    private static final int MAX_SETTLEMENT_DATE_WINDOW_DAYS = 4;

    private final StatementSettlementRepository settlementRepository;

    public SettlementResolutionService(StatementSettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    public record SettlementResolutionOutcome(
            boolean isSettlementOrRefund,
            boolean isResolved,
            StatementSettlement currentSettlement,
            StatementSettlement matchedSettlement
    ) {
        public static SettlementResolutionOutcome notSettlement() {
            return new SettlementResolutionOutcome(false, false, null, null);
        }

        public static SettlementResolutionOutcome resolved(StatementSettlement current, StatementSettlement matched) {
            return new SettlementResolutionOutcome(true, true, current, matched);
        }

        public static SettlementResolutionOutcome unresolved(StatementSettlement current) {
            return new SettlementResolutionOutcome(true, false, current, null);
        }
    }

    /**
     * Inspects a candidate. If it represents a settlement (PAYMENT), internal transfer (TRANSFER),
     * or refund/reversal (REFUND/REVERSAL), resolves it against existing records or records it as unresolved.
     */
    @Transactional
    public SettlementResolutionOutcome processCandidate(
            User user,
            FinancialDocument document,
            TransactionCandidate candidate
    ) {
        SourceTransactionClassification classification = candidate.classification();
        if (classification != SourceTransactionClassification.PAYMENT
                && classification != SourceTransactionClassification.REFUND
                && classification != SourceTransactionClassification.REVERSAL) {
            return SettlementResolutionOutcome.notSettlement();
        }

        // If a settlement record was already created for this document and row (e.g. re-processing):
        Optional<StatementSettlement> existingForDocRow = settlementRepository.findByDocumentIdAndRowIndex(
                document.getId(), candidate.rowIndex());
        if (existingForDocRow.isPresent()) {
            StatementSettlement existingDocSettlement = existingForDocRow.get();
            if (existingDocSettlement.getStatus() == SettlementStatus.RESOLVED) {
                StatementSettlement matched = existingDocSettlement.getMatchedSettlementId() != null
                        ? settlementRepository.findById(existingDocSettlement.getMatchedSettlementId()).orElse(null)
                        : null;
                return SettlementResolutionOutcome.resolved(existingDocSettlement, matched);
            }
            if (classification == SourceTransactionClassification.REFUND
                    || classification == SourceTransactionClassification.REVERSAL) {
                return SettlementResolutionOutcome.unresolved(existingDocSettlement);
            }
            DocumentAccountType currentAccountType = candidate.accountType() != null
                    ? candidate.accountType()
                    : document.getAccountType();
            List<StatementSettlement> counterpartCandidates = settlementRepository.findByUserIdAndStatusAndAmount(
                    user.getId(),
                    SettlementStatus.UNRESOLVED,
                    candidate.amount()
            );
            Optional<StatementSettlement> match = counterpartCandidates.stream()
                    .filter(c -> !c.getId().equals(existingDocSettlement.getId()))
                    .filter(c -> !c.getDocument().getId().equals(document.getId()))
                    .filter(c -> isOpposingOrComplementary(c, classification, currentAccountType))
                    .filter(c -> Math.abs(ChronoUnit.DAYS.between(c.getTransactionDate(), candidate.transactionDate())) <= MAX_SETTLEMENT_DATE_WINDOW_DAYS)
                    .findFirst();
            if (match.isPresent()) {
                StatementSettlement counterpart = match.get();
                existingDocSettlement.markResolved(counterpart.getId());
                counterpart.markResolved(existingDocSettlement.getId());
                settlementRepository.save(existingDocSettlement);
                settlementRepository.save(counterpart);
                return SettlementResolutionOutcome.resolved(existingDocSettlement, counterpart);
            }
            return SettlementResolutionOutcome.unresolved(existingDocSettlement);
        }

        // For refunds and reversals, store as UNRESOLVED offset
        if (classification == SourceTransactionClassification.REFUND
                || classification == SourceTransactionClassification.REVERSAL) {
            StatementSettlement refund = new StatementSettlement(
                    user,
                    document,
                    candidate.rowIndex(),
                    candidate.transactionDate(),
                    candidate.amount(),
                    candidate.rawMerchant(),
                    candidate.rawDescription(),
                    classification,
                    candidate.accountType() != null ? candidate.accountType() : document.getAccountType(),
                    candidate.externalReference(),
                    SettlementStatus.UNRESOLVED
            );
            StatementSettlement saved = settlementRepository.save(refund);
            log.info("Recorded unresolved refund {} from document {} for user {}",
                    saved.getId(), document.getId(), user.getId());
            return SettlementResolutionOutcome.unresolved(saved);
        }

        // For PAYMENT and TRANSFER, search for an opposing unresolved counterpart
        DocumentAccountType currentAccountType = candidate.accountType() != null
                ? candidate.accountType()
                : document.getAccountType();

        List<StatementSettlement> candidates = settlementRepository.findByUserIdAndStatusAndAmount(
                user.getId(),
                SettlementStatus.UNRESOLVED,
                candidate.amount()
        );

        Optional<StatementSettlement> match = candidates.stream()
                .filter(existing -> !existing.getDocument().getId().equals(document.getId()) || existing.getRowIndex() != candidate.rowIndex())
                .filter(existing -> isOpposingOrComplementary(existing, classification, currentAccountType))
                .filter(existing -> Math.abs(ChronoUnit.DAYS.between(existing.getTransactionDate(), candidate.transactionDate())) <= MAX_SETTLEMENT_DATE_WINDOW_DAYS)
                .findFirst();

        if (match.isPresent()) {
            StatementSettlement existing = match.get();
            StatementSettlement current = new StatementSettlement(
                    user,
                    document,
                    candidate.rowIndex(),
                    candidate.transactionDate(),
                    candidate.amount(),
                    candidate.rawMerchant(),
                    candidate.rawDescription(),
                    classification,
                    currentAccountType,
                    candidate.externalReference(),
                    SettlementStatus.RESOLVED
            );
            current.markResolved(existing.getId());
            StatementSettlement savedCurrent = settlementRepository.save(current);

            existing.markResolved(savedCurrent.getId());
            settlementRepository.save(existing);

            log.info("Resolved settlement between document {} (row {}) and document {} (row {}) for user {} amount {}",
                    document.getId(), candidate.rowIndex(), existing.getDocument().getId(), existing.getRowIndex(),
                    user.getId(), candidate.amount());
            return SettlementResolutionOutcome.resolved(savedCurrent, existing);
        } else {
            StatementSettlement current = new StatementSettlement(
                    user,
                    document,
                    candidate.rowIndex(),
                    candidate.transactionDate(),
                    candidate.amount(),
                    candidate.rawMerchant(),
                    candidate.rawDescription(),
                    classification,
                    currentAccountType,
                    candidate.externalReference(),
                    SettlementStatus.UNRESOLVED
            );
            StatementSettlement saved = settlementRepository.save(current);
            log.info("Recorded unresolved {} {} from document {} for user {}",
                    classification, saved.getId(), document.getId(), user.getId());
            return SettlementResolutionOutcome.unresolved(saved);
        }
    }

    private boolean isOpposingOrComplementary(
            StatementSettlement existing,
            SourceTransactionClassification newClassification,
            DocumentAccountType newAccountType
    ) {
        if (newClassification == SourceTransactionClassification.PAYMENT) {
            // Match BANK (SAVINGS/CURRENT) payment with CREDIT_CARD payment received
            boolean newIsCard = newAccountType == DocumentAccountType.CREDIT_CARD;
            boolean existingIsCard = existing.getAccountType() == DocumentAccountType.CREDIT_CARD;
            return (newIsCard && !existingIsCard) || (!newIsCard && existingIsCard);
        }

        return false;
    }
}
