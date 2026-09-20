package com.spendsense.ingestion;

import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.document.DocumentStatus;
import com.spendsense.document.FinancialDocument;
import com.spendsense.document.FinancialDocumentRepository;
import com.spendsense.document.FinancialDocumentService;
import com.spendsense.document.StatementController;
import com.spendsense.document.storage.DocumentStorage;
import com.spendsense.document.storage.StorageReference;
import com.spendsense.ingestion.extractor.DocumentExtractor;
import com.spendsense.ingestion.service.StatementImportResult;
import com.spendsense.ingestion.service.StatementIngestionService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementIngestionAnalyticsIntegrationTest {

    @Mock private FinancialDocumentRepository documentRepository;
    @Mock private DocumentStorage documentStorage;
    @Mock private CandidateValidator candidateValidator;
    @Mock private com.spendsense.ingestion.dedup.DeduplicationService deduplicationService;
    @Mock private com.spendsense.ingestion.settlement.SettlementResolutionService settlementResolutionService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;
    @Mock private DocumentExtractor documentExtractor;
    @Mock private StatementSettlementRepository settlementRepository;
    @Mock private FinancialDocumentService financialDocumentService;

    private User user;
    private User attacker;
    private FinancialDocument document;
    private StatementIngestionService ingestionService;
    private AnalyticsService analyticsService;
    private StatementController statementController;

    private List<Transaction> persistedTransactions;
    private List<StatementSettlement> persistedSettlements;

    @BeforeEach
    void setUp() {
        user = new User("rahul@example.com", "hash", "Rahul Sen", "Asia/Kolkata");
        attacker = new User("mallory@example.com", "hash", "Mallory", "Asia/Kolkata");

        lenient().when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        persistedTransactions = new ArrayList<>();
        persistedSettlements = new ArrayList<>();

        document = new FinancialDocument(
                user,
                "bank_statement_aug2026.csv",
                DocumentFileType.CSV,
                1024L,
                user.getId() + "/doc-1.bin",
                "hash123456",
                "HDFC Bank",
                DocumentAccountType.SAVINGS,
                "1234",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );

        lenient().when(documentRepository.findByIdAndUserId(document.getId(), user.getId()))
                .thenReturn(Optional.of(document));

        lenient().when(documentExtractor.supports(DocumentFileType.CSV)).thenReturn(true);

        ingestionService = new StatementIngestionService(
                documentRepository,
                documentStorage,
                List.of(documentExtractor),
                candidateValidator,
                deduplicationService,
                settlementResolutionService,
                null,
                transactionRepository,
                userRepository,
                currentUser
        );

        analyticsService = new AnalyticsService(transactionRepository, currentUser);

        statementController = new StatementController(
                financialDocumentService,
                ingestionService,
                settlementRepository
        );
    }

    @Test
    @DisplayName("Financial Semantics & Double-Counting: Settlements and refunds never distort analytics totals")
    void settlementsAndRefundsNeverDistortAnalytics() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);

        // 1. Initial State: Known Income (₹50,000) and Expense (₹1,000)
        BigDecimal initialIncome = new BigDecimal("50000.00");
        BigDecimal initialExpense = new BigDecimal("1000.00");

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, from, to))
                .thenReturn(initialIncome);
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(initialExpense);
        when(transactionRepository.countByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(1L);
        when(transactionRepository.countByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, from, to))
                .thenReturn(1L);

        SpendingSummaryResponse initialSummary = analyticsService.getSummary(from, to);
        assertThat(initialSummary.totalIncome()).isEqualByComparingTo("50000.00");
        assertThat(initialSummary.totalExpenses()).isEqualByComparingTo("1000.00");
        assertThat(initialSummary.netSavings()).isEqualByComparingTo("49000.00");

        // 2. Prepare Statement Candidates:
        // - Row 1: Swiggy purchase (EXPENSE, ₹500) -> GENUINE EXPENSE
        // - Row 2: Monthly Salary (INCOME, ₹80,000) -> GENUINE INCOME
        // - Row 3: Credit Card Bill Payment (PAYMENT, ₹25,000) -> SETTLEMENT (Excluded)
        // - Row 4: Amazon Refund (REFUND, ₹2,000) -> REFUND OFFSET (Excluded from Income)
        // - Row 5: Own Account Transfer (TRANSFER, ₹10,000) -> TRANSFER (Excluded)

        TransactionCandidate row1 = TransactionCandidate.builder()
                .documentId(document.getId())
                .rowIndex(1)
                .transactionDate(LocalDate.of(2026, 8, 5))
                .amount(new BigDecimal("500.00"))
                .rawMerchant("Swiggy")
                .rawDescription("Food delivery")
                .classification(SourceTransactionClassification.PURCHASE)
                .direction(TransactionType.EXPENSE)
                .inferredPaymentMethod(PaymentMethod.UPI)
                .accountType(DocumentAccountType.SAVINGS)
                .externalReference("UPI12345")
                .build();

        TransactionCandidate row2 = TransactionCandidate.builder()
                .documentId(document.getId())
                .rowIndex(2)
                .transactionDate(LocalDate.of(2026, 8, 1))
                .amount(new BigDecimal("80000.00"))
                .rawMerchant("Acme Corp")
                .rawDescription("Salary Credit")
                .classification(SourceTransactionClassification.DEPOSIT)
                .direction(TransactionType.INCOME)
                .inferredPaymentMethod(PaymentMethod.BANK_TRANSFER)
                .accountType(DocumentAccountType.SAVINGS)
                .externalReference("SAL98765")
                .build();

        TransactionCandidate row3 = TransactionCandidate.builder()
                .documentId(document.getId())
                .rowIndex(3)
                .transactionDate(LocalDate.of(2026, 8, 10))
                .amount(new BigDecimal("25000.00"))
                .rawMerchant("HDFC CC Payment")
                .rawDescription("Bill Desk")
                .classification(SourceTransactionClassification.PAYMENT)
                .direction(null)
                .inferredPaymentMethod(PaymentMethod.BANK_TRANSFER)
                .accountType(DocumentAccountType.SAVINGS)
                .externalReference("CCPAY111")
                .build();

        TransactionCandidate row4 = TransactionCandidate.builder()
                .documentId(document.getId())
                .rowIndex(4)
                .transactionDate(LocalDate.of(2026, 8, 15))
                .amount(new BigDecimal("2000.00"))
                .rawMerchant("Amazon")
                .rawDescription("Refund for Order 99")
                .classification(SourceTransactionClassification.REFUND)
                .direction(null)
                .inferredPaymentMethod(PaymentMethod.UPI)
                .accountType(DocumentAccountType.SAVINGS)
                .externalReference("REFUND222")
                .build();

        TransactionCandidate row5 = TransactionCandidate.builder()
                .documentId(document.getId())
                .rowIndex(5)
                .transactionDate(LocalDate.of(2026, 8, 20))
                .amount(new BigDecimal("10000.00"))
                .rawMerchant("Transfer to Self")
                .rawDescription("Savings to Current")
                .classification(SourceTransactionClassification.TRANSFER)
                .direction(null)
                .inferredPaymentMethod(PaymentMethod.BANK_TRANSFER)
                .accountType(DocumentAccountType.SAVINGS)
                .externalReference("TRF333")
                .build();

        when(documentStorage.retrieve(eq(user.getId()), any(StorageReference.class)))
                .thenReturn(new ByteArrayInputStream("dummy".getBytes()));
        when(documentExtractor.extract(eq(document.getId()), eq(DocumentAccountType.SAVINGS), any(InputStream.class)))
                .thenReturn(List.of(row1, row2, row3, row4, row5));

        when(candidateValidator.validate(any())).thenReturn(CandidateValidationResult.valid());
        when(deduplicationService.checkDuplicate(any(), any(), any()))
                .thenReturn(com.spendsense.ingestion.dedup.DuplicateCheckResult.unique());

        // Settlement resolution mock outcomes
        StatementSettlement paymentSettlement = new StatementSettlement(user, document, 3, row3.transactionDate(), row3.amount(), row3.rawMerchant(), row3.rawDescription(), SourceTransactionClassification.PAYMENT, DocumentAccountType.SAVINGS, row3.externalReference(), SettlementStatus.RESOLVED);
        StatementSettlement refundSettlement = new StatementSettlement(user, document, 4, row4.transactionDate(), row4.amount(), row4.rawMerchant(), row4.rawDescription(), SourceTransactionClassification.REFUND, DocumentAccountType.SAVINGS, row4.externalReference(), SettlementStatus.UNRESOLVED);
        StatementSettlement transferSettlement = new StatementSettlement(user, document, 5, row5.transactionDate(), row5.amount(), row5.rawMerchant(), row5.rawDescription(), SourceTransactionClassification.TRANSFER, DocumentAccountType.SAVINGS, row5.externalReference(), SettlementStatus.UNRESOLVED);

        when(settlementResolutionService.processCandidate(eq(user), eq(document), eq(row1)))
                .thenReturn(com.spendsense.ingestion.settlement.SettlementResolutionService.SettlementResolutionOutcome.notSettlement());
        when(settlementResolutionService.processCandidate(eq(user), eq(document), eq(row2)))
                .thenReturn(com.spendsense.ingestion.settlement.SettlementResolutionService.SettlementResolutionOutcome.notSettlement());
        when(settlementResolutionService.processCandidate(eq(user), eq(document), eq(row3)))
                .thenReturn(com.spendsense.ingestion.settlement.SettlementResolutionService.SettlementResolutionOutcome.resolved(paymentSettlement, null));
        when(settlementResolutionService.processCandidate(eq(user), eq(document), eq(row4)))
                .thenReturn(com.spendsense.ingestion.settlement.SettlementResolutionService.SettlementResolutionOutcome.unresolved(refundSettlement));
        when(settlementResolutionService.processCandidate(eq(user), eq(document), eq(row5)))
                .thenReturn(com.spendsense.ingestion.settlement.SettlementResolutionService.SettlementResolutionOutcome.unresolved(transferSettlement));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            persistedTransactions.add(tx);
            return tx;
        });

        // Ingest Document
        StatementImportResult result = ingestionService.ingestDocument(document.getId());

        // 3. Verification of Ingestion Output
        assertThat(result.totalRows()).isEqualTo(5);
        assertThat(result.importedTransactions()).isEqualTo(2); // Only Swiggy and Salary
        assertThat(result.settlementsResolved()).isEqualTo(1); // Row 3
        assertThat(result.unresolvedCandidates()).isEqualTo(2); // Row 4 (Refund) + Row 5 (Transfer)
        assertThat(persistedTransactions).hasSize(2);

        // Verify that persisted transactions contain ONLY genuine expenses/income
        Transaction tx1 = persistedTransactions.get(0);
        assertThat(tx1.getMerchant()).isEqualTo("Swiggy");
        assertThat(tx1.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx1.getAmount()).isEqualByComparingTo("500.00");
        assertThat(tx1.getSourceType()).isEqualTo(TransactionSourceType.STATEMENT_IMPORT);

        Transaction tx2 = persistedTransactions.get(1);
        assertThat(tx2.getMerchant()).isEqualTo("Acme Corp");
        assertThat(tx2.getTransactionType()).isEqualTo(TransactionType.INCOME);
        assertThat(tx2.getAmount()).isEqualByComparingTo("80000.00");
        assertThat(tx2.getSourceType()).isEqualTo(TransactionSourceType.STATEMENT_IMPORT);

        // 4. Analytics Verification Post-Import
        // Updated income: 50,000 + 80,000 = 130,000 (Refund ₹2,000 is NOT income!)
        // Updated expense: 1,000 + 500 = 1,500 (CC Payment ₹25,000 and Transfer ₹10,000 are NOT expenses!)
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, from, to))
                .thenReturn(new BigDecimal("130000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("1500.00"));
        when(transactionRepository.countByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(2L);
        when(transactionRepository.countByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, from, to))
                .thenReturn(2L);

        SpendingSummaryResponse updatedSummary = analyticsService.getSummary(from, to);
        assertThat(updatedSummary.totalIncome()).isEqualByComparingTo("130000.00");
        assertThat(updatedSummary.totalExpenses()).isEqualByComparingTo("1500.00");
        assertThat(updatedSummary.netSavings()).isEqualByComparingTo("128500.00");
    }

    @Test
    @DisplayName("Security & IDOR: Controller prevents cross-user access to statement settlements")
    void preventsCrossUserAccessToSettlements() {
        UUID docId = UUID.randomUUID();
        when(financialDocumentService.get(docId))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found"));

        assertThatThrownBy(() -> statementController.listSettlements(docId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Document not found");

        verify(settlementRepository, never()).findByDocumentId(any());
    }
}
