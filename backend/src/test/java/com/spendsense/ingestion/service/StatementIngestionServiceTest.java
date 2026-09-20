package com.spendsense.ingestion.service;

import com.spendsense.categorization.CategorizationRequest;
import com.spendsense.categorization.CategorizationResult;
import com.spendsense.categorization.CategorizationService;
import com.spendsense.categorization.CategorizationSource;
import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.document.DocumentStatus;
import com.spendsense.document.FinancialDocument;
import com.spendsense.document.FinancialDocumentRepository;
import com.spendsense.document.storage.DocumentStorage;
import com.spendsense.document.storage.StorageReference;
import com.spendsense.ingestion.CandidateValidationResult;
import com.spendsense.ingestion.CandidateValidator;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.ingestion.dedup.DeduplicationService;
import com.spendsense.ingestion.extractor.DocumentExtractor;
import com.spendsense.ingestion.settlement.SettlementResolutionService;
import com.spendsense.ingestion.settlement.SettlementStatus;
import com.spendsense.ingestion.settlement.StatementSettlement;
import com.spendsense.ingestion.settlement.StatementSettlementRepository;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionSourceType;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatementIngestionServiceTest {

    @Mock
    private FinancialDocumentRepository documentRepository;

    @Mock
    private DocumentStorage documentStorage;

    @Mock
    private DocumentExtractor csvExtractor;

    @Mock
    private CategorizationService categorizationService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private StatementSettlementRepository settlementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUser currentUser;

    private CandidateValidator candidateValidator;
    private DeduplicationService deduplicationService;
    private SettlementResolutionService settlementResolutionService;
    private StatementIngestionService ingestionService;

    private User testUser;
    private User otherUser;
    private SpendSenseUserPrincipal principal;

    @BeforeEach
    void setUp() {
        candidateValidator = new CandidateValidator();
        deduplicationService = new DeduplicationService();
        settlementResolutionService = new SettlementResolutionService(settlementRepository);

        ingestionService = new StatementIngestionService(
                documentRepository,
                documentStorage,
                List.of(csvExtractor),
                candidateValidator,
                deduplicationService,
                settlementResolutionService,
                categorizationService,
                transactionRepository,
                userRepository,
                currentUser
        );

        testUser = new User("harshit@example.com", "hash123", "Harshit", "Asia/Kolkata");
        otherUser = new User("alice@example.com", "hash456", "Alice", "Asia/Kolkata");
        principal = new SpendSenseUserPrincipal(testUser);

        lenient().when(currentUser.require()).thenReturn(principal);
        lenient().when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        lenient().when(csvExtractor.supports(DocumentFileType.CSV)).thenReturn(true);

        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(settlementRepository.save(any(StatementSettlement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private FinancialDocument createDocument(UUID docId, DocumentAccountType accountType) {
        return new FinancialDocument(
                testUser, "statement.csv", DocumentFileType.CSV, 1024, "key123", "hash123",
                "HDFC Bank", accountType, "4321", null, null);
    }

    private TransactionCandidate createCandidate(
            UUID docId, int rowIndex, LocalDate date, BigDecimal amount, String merchant,
            TransactionType direction, SourceTransactionClassification classification,
            PaymentMethod method, String extRef
    ) {
        return createCandidate(docId, rowIndex, date, amount, merchant, direction, classification, method, extRef, DocumentAccountType.SAVINGS);
    }

    private TransactionCandidate createCandidate(
            UUID docId, int rowIndex, LocalDate date, BigDecimal amount, String merchant,
            TransactionType direction, SourceTransactionClassification classification,
            PaymentMethod method, String extRef, DocumentAccountType accountType
    ) {
        return new TransactionCandidate(
                docId, rowIndex, date, merchant, merchant + " narration",
                amount, direction, classification, method, extRef, null,
                accountType, "INR", "DR", CandidateValidationResult.valid());
    }

    private void setupDocumentExtraction(FinancialDocument document, List<TransactionCandidate> candidates) throws Exception {
        when(documentRepository.findByIdAndUserId(document.getId(), testUser.getId())).thenReturn(Optional.of(document));
        when(documentStorage.retrieve(eq(testUser.getId()), any(StorageReference.class)))
                .thenReturn(new ByteArrayInputStream("header\nrow".getBytes()));
        when(csvExtractor.extract(eq(document.getId()), eq(document.getAccountType()), any(InputStream.class)))
                .thenReturn(candidates);
    }

    // A. Normal bank expense
    @Test
    @DisplayName("Scenario A: Normal bank expense is categorized, persisted, and traced")
    void scenarioA_NormalBankExpense() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate candidate = createCandidate(
                doc.getId(), 1, LocalDate.of(2026, 3, 10), new BigDecimal("450.00"),
                "Swiggy", TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE,
                PaymentMethod.UPI, "UPI987654321012");

        setupDocumentExtraction(doc, List.of(candidate));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(categorizationService.categorize(any(CategorizationRequest.class)))
                .thenReturn(CategorizationResult.fromRule(
                        TransactionCategory.FOOD, 0.95, ConfidenceLevel.HIGH, "RULE_FOOD", "Food match", "swiggy"));

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(1, result.importedTransactions());
        assertEquals(0, result.exactDuplicates());
        assertEquals(0, result.unresolvedCandidates());
        assertEquals(DocumentStatus.COMPLETED, doc.getStatus());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertEquals(new BigDecimal("450.00"), saved.getAmount());
        assertEquals(TransactionType.EXPENSE, saved.getTransactionType());
        assertEquals(TransactionCategory.FOOD, saved.getCategory());
        assertEquals(TransactionSourceType.STATEMENT_IMPORT, saved.getSourceType());
        assertEquals(doc.getId(), saved.getSourceDocument().getId());
        assertTrue(saved.getSourceReference().contains("row:1"));
    }

    // B. Normal salary/deposit
    @Test
    @DisplayName("Scenario B: Normal salary deposit is persisted as INCOME")
    void scenarioB_NormalSalaryDeposit() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate candidate = createCandidate(
                doc.getId(), 1, LocalDate.of(2026, 3, 1), new BigDecimal("125000.00"),
                "TechCorp Salary", TransactionType.INCOME, SourceTransactionClassification.DEPOSIT,
                PaymentMethod.BANK_TRANSFER, "NEFT12345");

        setupDocumentExtraction(doc, List.of(candidate));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(1, result.importedTransactions());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertEquals(TransactionType.INCOME, saved.getTransactionType());
        assertEquals(new BigDecimal("125000.00"), saved.getAmount());
    }

    // C. UPI expense
    @Test
    @DisplayName("Scenario C: UPI purchase preserves inferred UPI payment method")
    void scenarioC_UpiExpense() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate candidate = createCandidate(
                doc.getId(), 1, LocalDate.of(2026, 3, 5), new BigDecimal("250.00"),
                "Chai Point", TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE,
                PaymentMethod.UPI, "UPI000111222333");

        setupDocumentExtraction(doc, List.of(candidate));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(1, result.importedTransactions());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(PaymentMethod.UPI, captor.getValue().getPaymentMethod());
    }

    // D. Credit-card purchase
    @Test
    @DisplayName("Scenario D: Credit-card purchase sets CREDIT_CARD payment method and EXPENSE")
    void scenarioD_CreditCardPurchase() throws Exception {
        FinancialDocument cardDoc = new FinancialDocument(
                testUser, "card.csv", DocumentFileType.CSV, 1024, "keyCard", "hashCard",
                "HDFC", DocumentAccountType.CREDIT_CARD, "9999", null, null);
        TransactionCandidate candidate = createCandidate(
                cardDoc.getId(), 1, LocalDate.of(2026, 3, 7), new BigDecimal("3499.00"),
                "Zara Retail", TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE,
                PaymentMethod.CREDIT_CARD, null, DocumentAccountType.CREDIT_CARD);

        setupDocumentExtraction(cardDoc, List.of(candidate));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());

        StatementImportResult result = ingestionService.ingestDocument(cardDoc.getId());

        assertEquals(1, result.importedTransactions());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(PaymentMethod.CREDIT_CARD, captor.getValue().getPaymentMethod());
        assertEquals(TransactionType.EXPENSE, captor.getValue().getTransactionType());
    }

    // E. Exact duplicate
    @Test
    @DisplayName("Scenario E: Exact duplicate against existing database transaction is discarded")
    void scenarioE_ExactDuplicate() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        LocalDate date = LocalDate.of(2026, 3, 10);
        BigDecimal amount = new BigDecimal("450.00");

        Transaction existing = new Transaction(
                testUser, amount, "Swiggy", "Swiggy narration", TransactionCategory.FOOD,
                TransactionType.EXPENSE, PaymentMethod.UPI, date, "Ref: UPI999");
        existing.setSourceReference("UPI999");

        TransactionCandidate candidate = createCandidate(
                doc.getId(), 1, date, amount, "Swiggy", TransactionType.EXPENSE,
                SourceTransactionClassification.PURCHASE, PaymentMethod.UPI, "UPI999");

        setupDocumentExtraction(doc, List.of(candidate));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of(existing));

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(0, result.importedTransactions());
        assertEquals(1, result.exactDuplicates());
        verify(transactionRepository, never()).save(any());
    }

    // F. Same-document duplicate
    @Test
    @DisplayName("Scenario F: Same-document repeated row is detected and imported only once")
    void scenarioF_SameDocumentDuplicate() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        LocalDate date = LocalDate.of(2026, 3, 10);
        BigDecimal amount = new BigDecimal("550.00");

        TransactionCandidate row1 = createCandidate(
                doc.getId(), 1, date, amount, "Blue Tokai", TransactionType.EXPENSE,
                SourceTransactionClassification.PURCHASE, PaymentMethod.UPI, null);
        TransactionCandidate row2 = createCandidate(
                doc.getId(), 2, date, amount, "Blue Tokai", TransactionType.EXPENSE,
                SourceTransactionClassification.PURCHASE, PaymentMethod.UPI, null);

        setupDocumentExtraction(doc, List.of(row1, row2));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(new ArrayList<>());

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(1, result.importedTransactions());
        assertEquals(1, result.exactDuplicates());
        assertEquals(2, result.totalRows());
    }

    // G. Probable duplicate
    @Test
    @DisplayName("Scenario G: Probable duplicate is imported with requiresReview = true")
    void scenarioG_ProbableDuplicate() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        LocalDate existingDate = LocalDate.of(2026, 3, 10);
        LocalDate candidateDate = LocalDate.of(2026, 3, 11);
        BigDecimal amount = new BigDecimal("850.00");

        Transaction existing = new Transaction(
                testUser, amount, "Uber India", "Uber trip", TransactionCategory.TRAVEL,
                TransactionType.EXPENSE, PaymentMethod.UPI, existingDate, null);

        TransactionCandidate candidate = createCandidate(
                doc.getId(), 1, candidateDate, amount, "Uber Rides", TransactionType.EXPENSE,
                SourceTransactionClassification.PURCHASE, PaymentMethod.UPI, null);

        setupDocumentExtraction(doc, List.of(candidate));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of(existing));

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(1, result.importedTransactions());
        assertEquals(1, result.probableDuplicates());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertTrue(captor.getValue().isRequiresReview());
    }

    // H. Different users with identical transactions
    @Test
    @DisplayName("Scenario H: Identical transaction belonging to another user must NOT be treated as duplicate")
    void scenarioH_DifferentUsersNotDuplicate() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        LocalDate date = LocalDate.of(2026, 3, 10);
        BigDecimal amount = new BigDecimal("999.00");

        // Transaction exists for otherUser, not testUser
        // Repository query for testUser returns empty list
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                eq(testUser.getId()), any(), any())).thenReturn(List.of());

        TransactionCandidate candidate = createCandidate(
                doc.getId(), 1, date, amount, "Netflix", TransactionType.EXPENSE,
                SourceTransactionClassification.PURCHASE, PaymentMethod.CREDIT_CARD, "NET123");

        setupDocumentExtraction(doc, List.of(candidate));

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(1, result.importedTransactions());
        assertEquals(0, result.exactDuplicates());
    }

    // I. Credit-card payment
    @Test
    @DisplayName("Scenario I: Credit-card payment received does not create income and records settlement")
    void scenarioI_CreditCardPayment() throws Exception {
        FinancialDocument cardDoc = new FinancialDocument(
                testUser, "card.csv", DocumentFileType.CSV, 1024, "keyCard", "hashCard",
                "HDFC", DocumentAccountType.CREDIT_CARD, "9999", null, null);
        TransactionCandidate paymentReceived = createCandidate(
                cardDoc.getId(), 1, LocalDate.of(2026, 3, 15), new BigDecimal("15000.00"),
                "PAYMENT RECEIVED", null, SourceTransactionClassification.PAYMENT,
                PaymentMethod.BANK_TRANSFER, null, DocumentAccountType.CREDIT_CARD);

        setupDocumentExtraction(cardDoc, List.of(paymentReceived));
        when(settlementRepository.findByUserIdAndStatusAndAmount(testUser.getId(), SettlementStatus.UNRESOLVED, new BigDecimal("15000.00")))
                .thenReturn(List.of());

        StatementImportResult result = ingestionService.ingestDocument(cardDoc.getId());

        assertEquals(0, result.importedTransactions());
        assertEquals(1, result.unresolvedCandidates());
        verify(transactionRepository, never()).save(any());
        verify(settlementRepository).save(any(StatementSettlement.class));
    }

    // J. Bank-side credit-card payment
    @Test
    @DisplayName("Scenario J: Bank-side credit card bill payment does not create expense and records settlement")
    void scenarioJ_BankSideCreditCardPayment() throws Exception {
        FinancialDocument bankDoc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate bankPayment = createCandidate(
                bankDoc.getId(), 1, LocalDate.of(2026, 3, 15), new BigDecimal("15000.00"),
                "PAYMENT TO HDFC CREDIT CARD", null, SourceTransactionClassification.PAYMENT,
                PaymentMethod.BANK_TRANSFER, null);

        setupDocumentExtraction(bankDoc, List.of(bankPayment));
        when(settlementRepository.findByUserIdAndStatusAndAmount(testUser.getId(), SettlementStatus.UNRESOLVED, new BigDecimal("15000.00")))
                .thenReturn(List.of());

        StatementImportResult result = ingestionService.ingestDocument(bankDoc.getId());

        assertEquals(0, result.importedTransactions());
        assertEquals(1, result.unresolvedCandidates());
        verify(transactionRepository, never()).save(any());
    }

    // K. Matching bank/card settlement
    @Test
    @DisplayName("Scenario K: Matching bank debit and card credit settlement resolves without creating income/expense")
    void scenarioK_MatchingBankCardSettlement() throws Exception {
        FinancialDocument cardDoc = new FinancialDocument(
                testUser, "card.csv", DocumentFileType.CSV, 1024, "keyCard", "hashCard",
                "HDFC", DocumentAccountType.CREDIT_CARD, "9999", null, null);

        StatementSettlement bankSettlement = new StatementSettlement(
                testUser, createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS), 1,
                LocalDate.of(2026, 3, 15), new BigDecimal("25000.00"),
                "PAYMENT TO HDFC CC", "PAYMENT TO HDFC CC",
                SourceTransactionClassification.PAYMENT, DocumentAccountType.SAVINGS,
                null, SettlementStatus.UNRESOLVED);

        TransactionCandidate cardPayment = createCandidate(
                cardDoc.getId(), 1, LocalDate.of(2026, 3, 16), new BigDecimal("25000.00"),
                "PAYMENT RECEIVED", null, SourceTransactionClassification.PAYMENT,
                PaymentMethod.BANK_TRANSFER, null, DocumentAccountType.CREDIT_CARD);

        setupDocumentExtraction(cardDoc, List.of(cardPayment));
        when(settlementRepository.findByUserIdAndStatusAndAmount(testUser.getId(), SettlementStatus.UNRESOLVED, new BigDecimal("25000.00")))
                .thenReturn(List.of(bankSettlement));

        StatementImportResult result = ingestionService.ingestDocument(cardDoc.getId());

        assertEquals(0, result.importedTransactions());
        assertEquals(1, result.settlementsResolved());
        assertEquals(0, result.unresolvedCandidates());
        verify(transactionRepository, never()).save(any());
    }

    // L. Unresolved payment
    @Test
    @DisplayName("Scenario L: Unresolved payment remains pending in settlements store")
    void scenarioL_UnresolvedPayment() throws Exception {
        FinancialDocument bankDoc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate bankPayment = createCandidate(
                bankDoc.getId(), 1, LocalDate.of(2026, 3, 10), new BigDecimal("5000.00"),
                "CREDIT CARD PAYMENT", null, SourceTransactionClassification.PAYMENT,
                PaymentMethod.BANK_TRANSFER, null);

        setupDocumentExtraction(bankDoc, List.of(bankPayment));
        when(settlementRepository.findByUserIdAndStatusAndAmount(any(), any(), any()))
                .thenReturn(List.of());

        StatementImportResult result = ingestionService.ingestDocument(bankDoc.getId());

        assertEquals(1, result.unresolvedCandidates());
        assertEquals(0, result.settlementsResolved());
    }

    // M. Refund
    @Test
    @DisplayName("Scenario M: Refund is stored as settlement offset and does NOT become INCOME")
    void scenarioM_RefundNotIncome() throws Exception {
        FinancialDocument cardDoc = new FinancialDocument(
                testUser, "card.csv", DocumentFileType.CSV, 1024, "keyCard", "hashCard",
                "HDFC", DocumentAccountType.CREDIT_CARD, "9999", null, null);
        TransactionCandidate refund = createCandidate(
                cardDoc.getId(), 1, LocalDate.of(2026, 3, 12), new BigDecimal("1200.00"),
                "AMAZON REFUND", null, SourceTransactionClassification.REFUND,
                PaymentMethod.CREDIT_CARD, null, DocumentAccountType.CREDIT_CARD);

        setupDocumentExtraction(cardDoc, List.of(refund));

        StatementImportResult result = ingestionService.ingestDocument(cardDoc.getId());

        assertEquals(0, result.importedTransactions());
        assertEquals(1, result.unresolvedCandidates());
        verify(transactionRepository, never()).save(any());
        verify(settlementRepository).save(any(StatementSettlement.class));
    }

    // N. Transfer
    @Test
    @DisplayName("Scenario N: Internal transfer is stored as settlement and does NOT become ordinary EXPENSE")
    void scenarioN_TransferNotExpense() throws Exception {
        FinancialDocument bankDoc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate transfer = createCandidate(
                bankDoc.getId(), 1, LocalDate.of(2026, 3, 14), new BigDecimal("10000.00"),
                "TRF TO SELF ACC", null, SourceTransactionClassification.TRANSFER,
                PaymentMethod.BANK_TRANSFER, null);

        setupDocumentExtraction(bankDoc, List.of(transfer));
        when(settlementRepository.findByUserIdAndStatusAndAmount(any(), any(), any()))
                .thenReturn(List.of());

        StatementImportResult result = ingestionService.ingestDocument(bankDoc.getId());

        assertEquals(0, result.importedTransactions());
        assertEquals(1, result.unresolvedCandidates());
        verify(transactionRepository, never()).save(any());
    }

    // O. Re-import same document
    @Test
    @DisplayName("Scenario O: Re-importing same statement results in 100% duplicate rejection and 0 new transactions")
    void scenarioO_ReImportSameDocument() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        LocalDate date = LocalDate.of(2026, 3, 10);
        BigDecimal amount = new BigDecimal("500.00");

        Transaction existing = new Transaction(
                testUser, amount, "Zomato", "Zomato narration", TransactionCategory.FOOD,
                TransactionType.EXPENSE, PaymentMethod.UPI, date, null);

        TransactionCandidate row = createCandidate(
                doc.getId(), 1, date, amount, "Zomato", TransactionType.EXPENSE,
                SourceTransactionClassification.PURCHASE, PaymentMethod.UPI, null);

        setupDocumentExtraction(doc, List.of(row));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of(existing));

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(0, result.importedTransactions());
        assertEquals(1, result.exactDuplicates());
        verify(transactionRepository, never()).save(any());
    }

    // P. Invalid candidate
    @Test
    @DisplayName("Scenario P: Invalid candidate (missing amount) is rejected with warning")
    void scenarioP_InvalidCandidate() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate invalidRow = new TransactionCandidate(
                doc.getId(), 1, LocalDate.of(2026, 3, 10), "Bad Row", "Missing amount",
                null, TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE,
                PaymentMethod.UPI, null, null, DocumentAccountType.SAVINGS, "INR", "DR",
                CandidateValidationResult.valid());

        setupDocumentExtraction(doc, List.of(invalidRow));

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(0, result.importedTransactions());
        assertEquals(1, result.invalidRows());
        assertEquals(DocumentStatus.FAILED, doc.getStatus());
    }

    // Q. Mixed valid + invalid statement
    @Test
    @DisplayName("Scenario Q: Mixed valid + invalid rows imports valid ones and marks document PARTIALLY_PROCESSED")
    void scenarioQ_MixedValidAndInvalid() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate validRow = createCandidate(
                doc.getId(), 1, LocalDate.of(2026, 3, 10), new BigDecimal("100.00"),
                "Valid Merchant", TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE,
                PaymentMethod.CASH, null);
        TransactionCandidate invalidRow = new TransactionCandidate(
                doc.getId(), 2, null, "Invalid Date", "Missing date",
                new BigDecimal("200.00"), TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE,
                PaymentMethod.CASH, null, null, DocumentAccountType.SAVINGS, "INR", "DR",
                CandidateValidationResult.valid());

        setupDocumentExtraction(doc, List.of(validRow, invalidRow));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(1, result.importedTransactions());
        assertEquals(1, result.invalidRows());
        assertEquals(2, result.totalRows());
        assertEquals(DocumentStatus.PARTIALLY_PROCESSED, doc.getStatus());
    }

    // R. Existing Phase 6 categorization integration
    @Test
    @DisplayName("Scenario R: Phase 6 categorization predictions are attached to persisted transaction")
    void scenarioR_CategorizationIntegration() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate row = createCandidate(
                doc.getId(), 1, LocalDate.of(2026, 3, 10), new BigDecimal("800.00"),
                "BookMyShow", TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE,
                PaymentMethod.UPI, null);

        setupDocumentExtraction(doc, List.of(row));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(categorizationService.categorize(any(CategorizationRequest.class)))
                .thenReturn(CategorizationResult.fromRule(
                        TransactionCategory.ENTERTAINMENT, 0.99, ConfidenceLevel.HIGH, "RULE_MOVIES",
                        "Movie tickets", "bookmyshow"));

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(1, result.importedTransactions());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertEquals(TransactionCategory.ENTERTAINMENT, saved.getCategory());
        assertEquals(CategorizationSource.RULE, saved.getCategorizationSource());
        assertEquals(ConfidenceLevel.HIGH, saved.getCategorizationConfidenceLevel());
        assertEquals(0, new BigDecimal("0.99").compareTo(saved.getCategorizationConfidence()));
    }

    // S. Source traceability
    @Test
    @DisplayName("Scenario S: Saved transaction preserves document ID, row index, and STATEMENT_IMPORT source type")
    void scenarioS_SourceTraceability() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        TransactionCandidate row = createCandidate(
                doc.getId(), 42, LocalDate.of(2026, 3, 10), new BigDecimal("120.00"),
                "Metro Rail", TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE,
                PaymentMethod.UPI, "REF42");

        setupDocumentExtraction(doc, List.of(row));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());

        ingestionService.ingestDocument(doc.getId());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertEquals(TransactionSourceType.STATEMENT_IMPORT, saved.getSourceType());
        assertEquals(doc.getId(), saved.getSourceDocument().getId());
        assertEquals("doc:" + doc.getId() + ":row:42:REF42", saved.getSourceReference());
    }

    // T. Correct document row counts/status
    @Test
    @DisplayName("Scenario T: Document record accurately stores totalRowsDetected, importedCount, duplicateCount, and status COMPLETED")
    void scenarioT_DocumentRowCountsAndStatus() throws Exception {
        FinancialDocument doc = createDocument(UUID.randomUUID(), DocumentAccountType.SAVINGS);
        LocalDate date = LocalDate.of(2026, 3, 10);

        Transaction existing = new Transaction(
                testUser, new BigDecimal("100.00"), "Dup Merchant", "desc", TransactionCategory.OTHER,
                TransactionType.EXPENSE, PaymentMethod.UPI, date, null);

        TransactionCandidate row1 = createCandidate(
                doc.getId(), 1, date, new BigDecimal("100.00"), "Dup Merchant",
                TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE, PaymentMethod.UPI, null);
        TransactionCandidate row2 = createCandidate(
                doc.getId(), 2, date, new BigDecimal("200.00"), "New Merchant",
                TransactionType.EXPENSE, SourceTransactionClassification.PURCHASE, PaymentMethod.UPI, null);

        setupDocumentExtraction(doc, List.of(row1, row2));
        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of(existing));

        StatementImportResult result = ingestionService.ingestDocument(doc.getId());

        assertEquals(2, result.totalRows());
        assertEquals(1, result.importedTransactions());
        assertEquals(1, result.exactDuplicates());
        assertEquals(DocumentStatus.COMPLETED, doc.getStatus());
        assertEquals(2, doc.getTotalRowsDetected());
        assertEquals(1, doc.getImportedCount());
        assertEquals(1, doc.getDuplicateCount());
    }
}
