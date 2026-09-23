package com.spendsense.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.CategorizationRequest;
import com.spendsense.categorization.CategorizationResult;
import com.spendsense.categorization.CategorizationService;
import com.spendsense.categorization.CategorizationSource;
import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.categorization.model.CategorizationCandidate;
import com.spendsense.common.exception.ApiException;
import com.spendsense.account.FinancialAccount;
import com.spendsense.account.FinancialAccountRepository;
import com.spendsense.account.FinancialAccountService;
import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.FinancialDocument;
import com.spendsense.document.FinancialDocumentRepository;
import com.spendsense.document.storage.DocumentStorage;
import com.spendsense.document.storage.StorageReference;
import com.spendsense.ingestion.CandidateValidationResult;
import com.spendsense.ingestion.CandidateValidator;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.ingestion.dedup.DeduplicationService;
import com.spendsense.ingestion.dedup.DuplicateCheckResult;
import com.spendsense.ingestion.extractor.DocumentExtractionException;
import com.spendsense.ingestion.extractor.DocumentExtractor;
import com.spendsense.ingestion.settlement.SettlementResolutionService;
import com.spendsense.ingestion.settlement.SettlementResolutionService.SettlementResolutionOutcome;
import com.spendsense.ingestion.transfer.TransferMatchingService;
import com.spendsense.security.CurrentUser;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionSourceType;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferDirection;
import com.spendsense.transaction.TransferMatchStatus;
import com.spendsense.transaction.dto.CategorizationCandidateDto;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Common ingestion pipeline orchestrator that processes statement candidates from any extractor (CSV, XLSX, PDF),
 * applies candidate validation, batch & database deduplication, settlement/transfer resolution, Phase 6 categorization,
 * and persists valid transactions while maintaining document traceability.
 */
@Service
public class StatementIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StatementIngestionService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FinancialDocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final List<DocumentExtractor> extractors;
    private final CandidateValidator candidateValidator;
    private final DeduplicationService deduplicationService;
    private final SettlementResolutionService settlementResolutionService;
    private final CategorizationService categorizationService;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final FinancialAccountService accountService;
    private final FinancialAccountRepository accountRepository;
    private final TransferMatchingService transferMatchingService;

    public StatementIngestionService(
            FinancialDocumentRepository documentRepository,
            DocumentStorage documentStorage,
            List<DocumentExtractor> extractors,
            CandidateValidator candidateValidator,
            DeduplicationService deduplicationService,
            SettlementResolutionService settlementResolutionService,
            CategorizationService categorizationService,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            CurrentUser currentUser
    ) {
        this(documentRepository, documentStorage, extractors, candidateValidator, deduplicationService,
                settlementResolutionService, categorizationService, transactionRepository, userRepository, currentUser,
                null, null, null);
    }

    @Autowired
    public StatementIngestionService(
            FinancialDocumentRepository documentRepository,
            DocumentStorage documentStorage,
            List<DocumentExtractor> extractors,
            CandidateValidator candidateValidator,
            DeduplicationService deduplicationService,
            SettlementResolutionService settlementResolutionService,
            @Autowired(required = false) CategorizationService categorizationService,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            CurrentUser currentUser,
            @Autowired(required = false) FinancialAccountService accountService,
            @Autowired(required = false) FinancialAccountRepository accountRepository,
            @Autowired(required = false) TransferMatchingService transferMatchingService
    ) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.extractors = extractors != null ? extractors : List.of();
        this.candidateValidator = candidateValidator;
        this.deduplicationService = deduplicationService;
        this.settlementResolutionService = settlementResolutionService;
        this.categorizationService = categorizationService;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.transferMatchingService = transferMatchingService;
    }

    /**
     * Ingests a stored financial document through the complete canonical pipeline.
     */
    @Transactional
    public StatementImportResult ingestDocument(UUID documentId) {
        User user = requireUser();
        FinancialDocument document = documentRepository.findByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found"));

        document.markParsing();
        documentRepository.save(document);

        DocumentExtractor extractor = extractors.stream()
                .filter(e -> e.supports(document.getFileType()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE",
                        "No extractor found supporting " + document.getFileType()));

        List<TransactionCandidate> candidates;
        StorageReference storageRef = new StorageReference(document.getStorageKey(), document.getOriginalFilename(), document.getFileSizeBytes());
        try (InputStream stream = documentStorage.retrieve(user.getId(), storageRef)) {
            candidates = extractor.extract(document.getId(), document.getAccountType(), stream);
        } catch (DocumentExtractionException e) {
            log.error("Failed to extract statement document {}: {}", documentId, e.getMessage());
            document.markFailed(e.getMessage());
            documentRepository.save(document);
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getErrorCode().name(), e.getMessage());
        } catch (IOException e) {
            log.error("Failed to retrieve document stream {}: {}", documentId, e.getMessage());
            document.markFailed("Failed to read document from storage");
            documentRepository.save(document);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Failed to retrieve document from storage");
        }

        int totalRows = candidates != null ? candidates.size() : 0;
        if (candidates == null || candidates.isEmpty()) {
            document.markCompleted(0, 0, 0);
            documentRepository.save(document);
            return StatementImportResult.builder()
                    .documentId(documentId)
                    .totalRows(0)
                    .validRows(0)
                    .invalidRows(0)
                    .importedTransactions(0)
                    .exactDuplicates(0)
                    .probableDuplicates(0)
                    .unresolvedCandidates(0)
                    .settlementsResolved(0)
                    .skippedRows(0)
                    .warnings(List.of("No transaction rows found in document"))
                    .build();
        }

        // Determine date range for statement and prefetch window
        LocalDate minDate = candidates.stream()
                .map(TransactionCandidate::transactionDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        LocalDate maxDate = candidates.stream()
                .map(TransactionCandidate::transactionDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (minDate != null && maxDate != null) {
            document.updateStatementDates(minDate, maxDate);
        }

        // Ensure account is linked to document
        if (document.getAccount() == null && accountService != null) {
            FinancialAccount account = accountService.getOrCreateForStatement(
                    user,
                    document.getInstitutionName(),
                    document.getAccountType(),
                    document.getAccountNumberLast4()
            );
            document.setAccount(account);
            documentRepository.save(document);
        }

        List<FinancialAccount> allUserAccounts = (accountRepository != null)
                ? accountRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId())
                : (document.getAccount() != null ? List.of(document.getAccount()) : List.of());

        // Bounded pre-fetch of existing user transactions
        List<Transaction> existingTransactions = new ArrayList<>();
        if (minDate != null && maxDate != null) {
            LocalDate queryFrom = minDate.minusDays(5);
            LocalDate queryTo = maxDate.plusDays(5);
            existingTransactions.addAll(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                    user.getId(), queryFrom, queryTo));
        }

        List<Transaction> accountExistingTransactions = existingTransactions.stream()
                .filter(t -> t.getAccount() == null || (document.getAccount() != null && t.getAccount().getId().equals(document.getAccount().getId())))
                .toList();

        int validRows = 0;
        int invalidRows = 0;
        int importedTransactions = 0;
        int exactDuplicates = 0;
        int probableDuplicates = 0;
        int unresolvedCandidates = 0;
        int settlementsResolved = 0;
        int skippedRows = 0;
        List<String> warnings = new ArrayList<>();
        Set<String> seenDocumentSignatures = new HashSet<>();

        for (TransactionCandidate candidate : candidates) {
            // 1. Candidate validation
            CandidateValidationResult valResult = candidateValidator.validate(candidate);
            if (!valResult.isValid()) {
                invalidRows++;
                skippedRows++;
                warnings.add(String.format("Row %d: %s", candidate.rowIndex(), valResult.getErrorMessage()));
                continue;
            }
            validRows++;

            // 2. Batch & Database Deduplication (scoped to this account)
            DuplicateCheckResult dupResult = deduplicationService.checkDuplicate(
                    candidate, accountExistingTransactions, seenDocumentSignatures);

            if (dupResult.isExactDuplicate()) {
                Transaction existingTx = dupResult.existingTransaction();
                if (existingTx != null && existingTx.getSourceDocument() != null
                        && document.getId().equals(existingTx.getSourceDocument().getId())) {
                    // Row already persisted for this statement in previous run: preserve imported count
                    importedTransactions++;
                } else {
                    exactDuplicates++;
                    skippedRows++;
                    warnings.add(String.format("Row %d: %s", candidate.rowIndex(), dupResult.reason()));
                }
                continue;
            }

            // 3. Settlement / Refund Resolution
            SettlementResolutionOutcome settlementOutcome = settlementResolutionService.processCandidate(
                    user, document, candidate);

            if (settlementOutcome.isSettlementOrRefund()) {
                if (settlementOutcome.isResolved()) {
                    settlementsResolved++;
                } else {
                    unresolvedCandidates++;
                }
                skippedRows++; // Do not insert settlement as ordinary expense/income
                continue;
            }

            // 4. Transaction Classification, Transfer Matching, and Categorization
            boolean isCredit = candidate.direction() == TransactionType.INCOME
                    || "CR".equalsIgnoreCase(candidate.rawTypeIndicator())
                    || (candidate.classification() == SourceTransactionClassification.DEPOSIT)
                    || (candidate.classification() == SourceTransactionClassification.INTEREST && candidate.accountType() != DocumentAccountType.CREDIT_CARD);
            boolean isDebit = !isCredit;

            Optional<Transaction> bilateralCounterpart = (transferMatchingService != null)
                    ? transferMatchingService.findCounterpartForCandidate(candidate, isCredit, document.getAccount(), existingTransactions, user)
                    : Optional.empty();

            boolean isCashWithdrawal = candidate.direction() == TransactionType.CASH_WITHDRAWAL
                    || (candidate.rawDescription() != null && CASH_WITHDRAWAL_PATTERN.matcher(candidate.rawDescription()).find());

            boolean isInvestment = candidate.direction() == TransactionType.INVESTMENT
                    || (isDebit && isInvestmentCandidate(candidate));

            boolean isTransfer = candidate.direction() == TransactionType.TRANSFER
                    || candidate.classification() == SourceTransactionClassification.TRANSFER
                    || bilateralCounterpart.isPresent()
                    || isTransferCandidate(candidate, user, document.getAccount(), allUserAccounts, existingTransactions, isCredit);

            TransactionType finalType;
            TransferDirection transferDirection = null;
            TransferMatchStatus transferMatchStatus = TransferMatchStatus.NONE;

            if (isCashWithdrawal) {
                finalType = TransactionType.CASH_WITHDRAWAL;
            } else if (isInvestment) {
                finalType = TransactionType.INVESTMENT;
            } else if (isTransfer) {
                finalType = TransactionType.TRANSFER;
                transferDirection = isCredit ? TransferDirection.IN : TransferDirection.OUT;
                transferMatchStatus = bilateralCounterpart.isPresent() ? TransferMatchStatus.AUTO_MATCHED : TransferMatchStatus.UNMATCHED;
            } else if (isCredit) {
                finalType = TransactionType.INCOME;
            } else {
                finalType = TransactionType.EXPENSE;
            }

            String merchant = cleanMerchant(candidate.rawMerchant(), candidate.rawDescription());
            String description = cleanDescription(candidate.rawDescription());

            TransactionCategory assignedCategory = TransactionCategory.OTHER;
            BigDecimal confidence = BigDecimal.ZERO;
            ConfidenceLevel confidenceLevel = ConfidenceLevel.NONE;
            CategorizationSource source = CategorizationSource.FALLBACK;
            boolean requiresReview = dupResult.isProbableDuplicate();
            TransactionCategory predictedCategory = TransactionCategory.OTHER;
            String alternatives = null;

            if (finalType == TransactionType.TRANSFER || finalType == TransactionType.CASH_WITHDRAWAL || finalType == TransactionType.INVESTMENT) {
                assignedCategory = TransactionCategory.OTHER;
                confidence = BigDecimal.ONE;
                confidenceLevel = ConfidenceLevel.HIGH;
                source = CategorizationSource.RULE;
                requiresReview = false;
                predictedCategory = TransactionCategory.OTHER;
            } else if (categorizationService != null) {
                try {
                    CategorizationRequest catReq = new CategorizationRequest(
                            user.getId(),
                            merchant,
                            description,
                            candidate.amount(),
                            candidate.inferredPaymentMethod() != null ? candidate.inferredPaymentMethod().name() : null,
                            candidate.transactionDate()
                    );
                    CategorizationResult catResult = categorizationService.categorize(catReq);
                    if (catResult != null) {
                        assignedCategory = catResult.category();
                        confidence = BigDecimal.valueOf(catResult.confidence());
                        confidenceLevel = catResult.confidenceLevel();
                        source = catResult.source();
                        requiresReview = requiresReview || catResult.requiresUserConfirmation();
                        predictedCategory = catResult.category();
                        if (catResult.alternatives() != null && !catResult.alternatives().isEmpty()) {
                            alternatives = serializeAlternatives(catResult.alternatives());
                        }
                    } else {
                        assignedCategory = TransactionCategory.OTHER;
                        requiresReview = true;
                    }
                } catch (Exception e) {
                    log.warn("Categorization failed safely for row {}: {}", candidate.rowIndex(), e.getMessage());
                    assignedCategory = TransactionCategory.OTHER;
                    requiresReview = true;
                }
            } else {
                assignedCategory = TransactionCategory.OTHER;
                requiresReview = true;
            }

            if (dupResult.isProbableDuplicate()) {
                probableDuplicates++;
                requiresReview = true;
                warnings.add(String.format("Row %d: %s", candidate.rowIndex(), dupResult.reason()));
            }

            String sourceReference = "doc:" + document.getId() + ":row:" + candidate.rowIndex();
            if (candidate.externalReference() != null && !candidate.externalReference().isBlank()) {
                sourceReference += ":" + candidate.externalReference().trim();
            }

            String notes = buildNotes(candidate, dupResult);

            Transaction transaction = new Transaction(
                    user,
                    candidate.amount(),
                    merchant,
                    description,
                    assignedCategory,
                    finalType,
                    candidate.inferredPaymentMethod() != null ? candidate.inferredPaymentMethod() : PaymentMethod.OTHER,
                    candidate.transactionDate(),
                    notes,
                    confidence,
                    confidenceLevel,
                    source,
                    requiresReview,
                    predictedCategory,
                    alternatives,
                    TransactionSourceType.STATEMENT_IMPORT,
                    document,
                    sourceReference,
                    document.getAccount(),
                    transferDirection,
                    null,
                    null,
                    transferMatchStatus
            );

            Transaction saved = transactionRepository.save(transaction);
            if (bilateralCounterpart.isPresent() && transferMatchingService != null) {
                transferMatchingService.retroactivelyUpgradeAndMatch(saved, bilateralCounterpart.get());
            } else if (finalType == TransactionType.TRANSFER && transferMatchingService != null) {
                transferMatchingService.attemptMatch(saved);
            }
            existingTransactions.add(saved);
            importedTransactions++;
        }

        // 5. Update FinancialDocument lifecycle stats & status
        if (totalRows > 0 && importedTransactions == 0 && settlementsResolved == 0 && unresolvedCandidates == 0 && exactDuplicates == 0 && invalidRows == totalRows) {
            document.markFailed("All " + totalRows + " rows failed validation");
        } else if (invalidRows > 0) {
            document.markPartiallyProcessed(totalRows, importedTransactions, exactDuplicates,
                    String.format("Imported %d transactions with %d invalid row(s)", importedTransactions, invalidRows));
        } else {
            document.markCompleted(totalRows, importedTransactions, exactDuplicates);
        }
        documentRepository.save(document);

        return StatementImportResult.builder()
                .documentId(documentId)
                .totalRows(totalRows)
                .validRows(validRows)
                .invalidRows(invalidRows)
                .importedTransactions(importedTransactions)
                .exactDuplicates(exactDuplicates)
                .probableDuplicates(probableDuplicates)
                .unresolvedCandidates(unresolvedCandidates)
                .settlementsResolved(settlementsResolved)
                .skippedRows(skippedRows)
                .warnings(warnings)
                .build();
    }

    private User requireUser() {
        UUID userId = currentUser.require().getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    private static String cleanMerchant(String rawMerchant, String rawDescription) {
        if (rawMerchant != null && !rawMerchant.isBlank()) {
            String clean = rawMerchant.trim();
            return clean.length() > 120 ? clean.substring(0, 120) : clean;
        }
        if (rawDescription != null && !rawDescription.isBlank()) {
            String clean = rawDescription.trim();
            return clean.length() > 120 ? clean.substring(0, 120) : clean;
        }
        return "Unknown";
    }

    private static String cleanDescription(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return null;
        }
        String clean = rawDescription.trim();
        return clean.length() > 255 ? clean.substring(0, 255) : clean;
    }

    private static String buildNotes(TransactionCandidate candidate, DuplicateCheckResult dupResult) {
        StringBuilder sb = new StringBuilder();
        if (candidate.balanceAfter() != null) {
            sb.append("Balance: ").append(candidate.balanceAfter());
        }
        if (candidate.externalReference() != null && !candidate.externalReference().isBlank()) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("Ref: ").append(candidate.externalReference().trim());
        }
        if (dupResult.isProbableDuplicate()) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("[REVIEW: Probable duplicate]");
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String serializeAlternatives(List<CategorizationCandidate> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return null;
        }
        try {
            List<CategorizationCandidateDto> dtos = alternatives.stream()
                    .limit(3)
                    .map(CategorizationCandidateDto::from)
                    .toList();
            return OBJECT_MAPPER.writeValueAsString(dtos);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize categorization alternatives: {}", e.getMessage());
            return null;
        }
    }

    // NOTE: Use the canonical pattern from TransferMatchingService — see isTransferCandidate()

    private static final java.util.regex.Pattern INVESTMENT_KEYWORDS = java.util.regex.Pattern.compile(
            "\\b(ZERODHA|GROWW|UPSTOX|ANGEL ONE|ANGEL BROKING|INDMONEY|KUVERA|PAYTM MONEY|COIN|MUTUAL FUND|NIPPON INDIA|HDFC MF|ICICI PRUDENTIAL|SBI MF|UTI MF|AXIS MF|PPF|NPS|SGB|NSE|BSE)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final java.util.regex.Pattern CASH_WITHDRAWAL_PATTERN = java.util.regex.Pattern.compile(
            "\\b(ATM WDL|ATM CASH|CASH WDL|CASH DEP|ATM[/\\-]CASH|ATW[/\\-]|ATW\\b)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private boolean isTransferCandidate(
            TransactionCandidate candidate,
            User user,
            FinancialAccount currentAccount,
            List<FinancialAccount> allUserAccounts,
            List<Transaction> existingTransactions,
            boolean isCredit
    ) {
        String combined = ((candidate.rawMerchant() != null ? candidate.rawMerchant() : "") + " "
                + (candidate.rawDescription() != null ? candidate.rawDescription() : "")).toLowerCase();

        // 1. Direct transfer keywords (canonical pattern from TransferMatchingService)
        if (TransferMatchingService.INTERNAL_TRANSFER_KEYWORDS.matcher(combined).find()) {
            return true;
        }

        // 2. Mentions another account of the user + supporting user name signal
        if (allUserAccounts != null && transferMatchingService != null && transferMatchingService.matchesUserNameSignal(user, combined)) {
            for (FinancialAccount acc : allUserAccounts) {
                if (currentAccount != null && acc.getId().equals(currentAccount.getId())) {
                    continue;
                }
                if (acc.getInstitutionName() != null && !acc.getInstitutionName().isBlank()) {
                    if (TransferMatchingService.containsInstitutionReference(combined, acc.getInstitutionName())) {
                        return true;
                    }
                }
                if (acc.getAccountNumberLast4() != null && !acc.getAccountNumberLast4().isBlank()) {
                    if (combined.contains(acc.getAccountNumberLast4().trim())) {
                        return true;
                    }
                }
            }
        }

        // 3. Opposing unmatched counterpart transfer exists in other account
        if (existingTransactions != null && candidate.amount() != null) {
            TransferDirection opposingDirection = isCredit ? TransferDirection.OUT : TransferDirection.IN;
            boolean hasCounterpart = existingTransactions.stream()
                    .filter(t -> t.getTransactionType() == TransactionType.TRANSFER)
                    .filter(t -> t.getTransferDirection() == opposingDirection)
                    .filter(t -> t.getTransferMatchStatus() == TransferMatchStatus.UNMATCHED)
                    .filter(t -> t.getAmount().compareTo(candidate.amount()) == 0)
                    .filter(t -> currentAccount == null || t.getAccount() == null || !t.getAccount().getId().equals(currentAccount.getId()))
                    .anyMatch(t -> Math.abs(java.time.temporal.ChronoUnit.DAYS.between(t.getTransactionDate(), candidate.transactionDate())) <= 3);
            if (hasCounterpart) {
                return true;
            }
        }

        return false;
    }

    private boolean isInvestmentCandidate(TransactionCandidate candidate) {
        String combined = ((candidate.rawMerchant() != null ? candidate.rawMerchant() : "") + " "
                + (candidate.rawDescription() != null ? candidate.rawDescription() : "")).toLowerCase();
        return INVESTMENT_KEYWORDS.matcher(combined).find();
    }
}
