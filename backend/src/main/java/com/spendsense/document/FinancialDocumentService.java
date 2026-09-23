package com.spendsense.document;

import com.spendsense.common.exception.ApiException;
import com.spendsense.document.dto.DocumentUploadCommand;
import com.spendsense.document.dto.FinancialDocumentResponse;
import com.spendsense.document.storage.DocumentStorage;
import com.spendsense.document.storage.StorageReference;
import com.spendsense.security.CurrentUser;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FinancialDocumentService {

    private static final Logger log = LoggerFactory.getLogger(FinancialDocumentService.class);

    private final FinancialDocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final com.spendsense.ingestion.settlement.StatementSettlementRepository settlementRepository;
    private final com.spendsense.transaction.TransactionRepository transactionRepository;
    private final com.spendsense.ingestion.transfer.TransferMatchingService transferMatchingService;

    public FinancialDocumentService(
            FinancialDocumentRepository documentRepository,
            DocumentStorage documentStorage,
            UserRepository userRepository,
            CurrentUser currentUser
    ) {
        this(documentRepository, documentStorage, userRepository, currentUser, null, null, null);
    }

    public FinancialDocumentService(
            FinancialDocumentRepository documentRepository,
            DocumentStorage documentStorage,
            UserRepository userRepository,
            CurrentUser currentUser,
            com.spendsense.ingestion.settlement.StatementSettlementRepository settlementRepository
    ) {
        this(documentRepository, documentStorage, userRepository, currentUser, settlementRepository, null, null);
    }

    public FinancialDocumentService(
            FinancialDocumentRepository documentRepository,
            DocumentStorage documentStorage,
            UserRepository userRepository,
            CurrentUser currentUser,
            com.spendsense.ingestion.settlement.StatementSettlementRepository settlementRepository,
            com.spendsense.transaction.TransactionRepository transactionRepository
    ) {
        this(documentRepository, documentStorage, userRepository, currentUser, settlementRepository, transactionRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FinancialDocumentService(
            FinancialDocumentRepository documentRepository,
            DocumentStorage documentStorage,
            UserRepository userRepository,
            CurrentUser currentUser,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.spendsense.ingestion.settlement.StatementSettlementRepository settlementRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.spendsense.transaction.TransactionRepository transactionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.spendsense.ingestion.transfer.TransferMatchingService transferMatchingService
    ) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.settlementRepository = settlementRepository;
        this.transactionRepository = transactionRepository;
        this.transferMatchingService = transferMatchingService;
    }

    /**
     * Uploads, calculates content SHA-256 hash, validates against duplicates,
     * stores file via DocumentStorage, and creates FinancialDocument metadata.
     */
    @Transactional
    public FinancialDocumentResponse upload(DocumentUploadCommand command) {
        Objects.requireNonNull(command, "Upload command cannot be null");
        User user = requireUser();

        DocumentFileType fileType;
        try {
            fileType = DocumentFileType.fromExtensionOrFilename(command.originalFilename());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", e.getMessage());
        }

        UUID documentId = UUID.randomUUID();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }

        StorageReference storageReference;
        String contentHash;

        try (DigestInputStream dis = new DigestInputStream(command.contentStream(), md)) {
            storageReference = documentStorage.store(
                    user.getId(),
                    documentId,
                    command.originalFilename(),
                    dis,
                    command.sizeBytes()
            );
            contentHash = HexFormat.of().formatHex(md.digest());
        } catch (IOException e) {
            log.error("Failed to store uploaded document file: {}", command.originalFilename(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Failed to store document file");
        }

        // Check for duplicate document already uploaded by the same user
        if (documentRepository.existsByUserIdAndContentHash(user.getId(), contentHash)) {
            try {
                documentStorage.delete(user.getId(), storageReference);
            } catch (IOException e) {
                log.warn("Failed to clean up rejected duplicate file: {}", storageReference.storageKey());
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_DOCUMENT",
                    "This document has already been uploaded by your account."
            );
        }

        FinancialDocument document = new FinancialDocument(
                user,
                command.originalFilename(),
                fileType,
                storageReference.sizeBytes(),
                storageReference.storageKey(),
                contentHash,
                command.institutionName(),
                command.accountType(),
                command.accountNumberLast4(),
                command.statementStartDate(),
                command.statementEndDate()
        );

        FinancialDocument saved = documentRepository.save(document);
        log.info("Uploaded financial document {} for user {}", saved.getId(), user.getId());
        return FinancialDocumentResponse.from(saved);
    }

    public FinancialDocumentResponse get(UUID documentId) {
        User user = requireUser();
        FinancialDocument doc = findOwned(documentId, user.getId());
        return FinancialDocumentResponse.from(doc);
    }

    public List<FinancialDocumentResponse> list() {
        User user = requireUser();
        return documentRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(FinancialDocumentResponse::from)
                .toList();
    }

    public InputStream retrieveContent(UUID documentId) throws IOException {
        User user = requireUser();
        FinancialDocument doc = findOwned(documentId, user.getId());
        StorageReference ref = new StorageReference(doc.getStorageKey(), doc.getOriginalFilename(), doc.getFileSizeBytes());
        return documentStorage.retrieve(user.getId(), ref);
    }

    @Transactional
    public void delete(UUID documentId) {
        User user = requireUser();
        FinancialDocument doc = findOwned(documentId, user.getId());

        // 1. Find and delete all statement-owned transactions created/imported from this document
        if (transactionRepository != null) {
            List<com.spendsense.transaction.Transaction> statementTransactions = transactionRepository.findBySourceDocumentIdAndUserId(doc.getId(), user.getId())
                    .stream()
                    .filter(t -> t.getSourceType() != com.spendsense.transaction.TransactionSourceType.MANUAL)
                    .toList();
            if (!statementTransactions.isEmpty()) {
                log.info("Deleting {} statement-imported transactions for document {}", statementTransactions.size(), doc.getId());
                if (transferMatchingService != null) {
                    transferMatchingService.unlinkBeforeDeletion(statementTransactions);
                }
                transactionRepository.deleteAll(statementTransactions);
                transactionRepository.flush();
            }
        }

        // 2. Unmatch any counterpart settlements in other documents before deleting this document's settlements
        if (settlementRepository != null) {
            List<com.spendsense.ingestion.settlement.StatementSettlement> settlements = settlementRepository.findByDocumentId(doc.getId());
            for (com.spendsense.ingestion.settlement.StatementSettlement settlement : settlements) {
                if (settlement.getMatchedSettlementId() != null) {
                    settlementRepository.findById(settlement.getMatchedSettlementId()).ifPresent(counterpart -> {
                        if (!doc.getId().equals(counterpart.getDocument().getId())) {
                            counterpart.markUnresolved();
                            settlementRepository.save(counterpart);
                        }
                    });
                }
            }
            if (!settlements.isEmpty()) {
                log.info("Deleting {} settlements for document {}", settlements.size(), doc.getId());
                settlementRepository.deleteAll(settlements);
                settlementRepository.flush();
            }
        }

        // 3. Delete physical storage file
        try {
            StorageReference ref = new StorageReference(doc.getStorageKey(), doc.getOriginalFilename(), doc.getFileSizeBytes());
            documentStorage.delete(user.getId(), ref);
        } catch (IOException e) {
            log.warn("Failed to delete physical file for document {}: {}", documentId, e.getMessage());
        }

        // 4. Delete document metadata
        documentRepository.delete(doc);
        documentRepository.flush();
        log.info("Deleted financial document {} and associated statement records for user {}", documentId, user.getId());
    }

    @Transactional
    public void updateStatus(
            UUID documentId,
            DocumentStatus status,
            int totalRowsDetected,
            int importedCount,
            int duplicateCount,
            String errorMessage
    ) {
        User user = requireUser();
        FinancialDocument doc = findOwned(documentId, user.getId());

        switch (status) {
            case PARSING -> doc.markParsing();
            case COMPLETED -> doc.markCompleted(totalRowsDetected, importedCount, duplicateCount);
            case PARTIALLY_PROCESSED -> doc.markPartiallyProcessed(totalRowsDetected, importedCount, duplicateCount, errorMessage);
            case FAILED -> doc.markFailed(errorMessage);
            case UPLOADED -> doc.updateProcessingStats(totalRowsDetected, importedCount, duplicateCount);
        }

        documentRepository.save(doc);
    }

    private FinancialDocument findOwned(UUID documentId, UUID userId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found"));
    }

    private User requireUser() {
        UUID userId = currentUser.require().getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }
}
