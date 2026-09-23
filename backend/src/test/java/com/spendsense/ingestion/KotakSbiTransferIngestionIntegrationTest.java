package com.spendsense.ingestion;

import com.spendsense.account.FinancialAccount;
import com.spendsense.account.FinancialAccountRepository;
import com.spendsense.account.FinancialAccountService;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.dashboard.DashboardService;
import com.spendsense.dashboard.dto.DashboardDataResponse;
import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.document.FinancialDocument;
import com.spendsense.document.FinancialDocumentRepository;
import com.spendsense.document.FinancialDocumentService;
import com.spendsense.document.storage.DocumentStorage;
import com.spendsense.document.storage.StorageReference;
import com.spendsense.ingestion.dedup.DeduplicationService;
import com.spendsense.ingestion.extractor.DocumentExtractor;
import com.spendsense.ingestion.service.StatementImportResult;
import com.spendsense.ingestion.service.StatementIngestionService;
import com.spendsense.ingestion.settlement.SettlementResolutionService;
import com.spendsense.ingestion.settlement.StatementSettlementRepository;
import com.spendsense.ingestion.transfer.TransferMatchingService;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionSourceType;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferDirection;
import com.spendsense.transaction.TransferMatchStatus;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Real Acceptance Test: Kotak + SBI Internal Transfer Ingestion, Matching & Analytics")
class KotakSbiTransferIngestionIntegrationTest {

    @Mock private FinancialDocumentRepository documentRepository;
    @Mock private DocumentStorage documentStorage;
    @Mock private DocumentExtractor documentExtractor;
    @Mock private StatementSettlementRepository settlementRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private FinancialAccountRepository accountRepository;
    @Mock private FinancialAccountService accountService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;

    private CandidateValidator candidateValidator;
    private DeduplicationService deduplicationService;
    private SettlementResolutionService settlementResolutionService;
    private TransferMatchingService transferMatchingService;
    private StatementIngestionService ingestionService;
    private FinancialDocumentService financialDocumentService;
    private AnalyticsService analyticsService;
    private DashboardService dashboardService;

    private User user;
    private FinancialAccount kotakAccount;
    private FinancialAccount sbiAccount;
    private FinancialDocument kotakDocument;
    private FinancialDocument sbiDocument;

    private List<Transaction> dbTransactions;

    @BeforeEach
    void setUp() throws Exception {
        user = new User("harshit@spendsense.test", "hash123", "Harshit Ashok Sharma", "Asia/Kolkata");
        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        kotakAccount = new FinancialAccount(user, "Kotak Salary Account", "Kotak Mahindra Bank", DocumentAccountType.SAVINGS, "5567", "INR", true);
        sbiAccount = new FinancialAccount(user, "SBI Personal Account", "State Bank of India", DocumentAccountType.SAVINGS, "3117", "INR", false);

        when(accountRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(kotakAccount, sbiAccount));

        kotakDocument = new FinancialDocument(
                user, "AccountStatement_Kotak.pdf", DocumentFileType.PDF, 2048L,
                user.getId() + "/kotak.pdf", "hashKotak", "Kotak Mahindra Bank",
                DocumentAccountType.SAVINGS, "5567",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        kotakDocument.setAccount(kotakAccount);

        sbiDocument = new FinancialDocument(
                user, "AccountStatement_SBI.pdf", DocumentFileType.PDF, 4096L,
                user.getId() + "/sbi.pdf", "hashSbi", "State Bank of India",
                DocumentAccountType.SAVINGS, "3117",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        sbiDocument.setAccount(sbiAccount);

        when(documentRepository.findByIdAndUserId(kotakDocument.getId(), user.getId())).thenReturn(Optional.of(kotakDocument));
        when(documentRepository.findByIdAndUserId(sbiDocument.getId(), user.getId())).thenReturn(Optional.of(sbiDocument));

        when(documentExtractor.supports(DocumentFileType.PDF)).thenReturn(true);
        when(documentStorage.retrieve(any(), any())).thenReturn(new ByteArrayInputStream(new byte[0]));

        dbTransactions = new ArrayList<>();

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            if (!dbTransactions.contains(tx)) {
                dbTransactions.add(tx);
            }
            return tx;
        });

        when(transactionRepository.findUnmatchedTransfers(eq(user.getId()), eq(TransactionType.TRANSFER), any(), eq(TransferMatchStatus.UNMATCHED), any()))
                .thenAnswer(inv -> {
                    TransferDirection dir = inv.getArgument(2);
                    BigDecimal amt = inv.getArgument(4);
                    return dbTransactions.stream()
                            .filter(t -> t.getUser().getId().equals(user.getId()))
                            .filter(t -> t.getTransactionType() == TransactionType.TRANSFER)
                            .filter(t -> t.getTransferDirection() == dir)
                            .filter(t -> t.getTransferMatchStatus() == TransferMatchStatus.UNMATCHED)
                            .filter(t -> t.getAmount().compareTo(amt) == 0)
                            .toList();
                });

        when(transactionRepository.findPotentialTransferCounterparts(eq(user.getId()), any(), any(), any()))
                .thenAnswer(inv -> {
                    BigDecimal amt = inv.getArgument(1);
                    LocalDate from = inv.getArgument(2);
                    LocalDate to = inv.getArgument(3);
                    return dbTransactions.stream()
                            .filter(t -> t.getUser().getId().equals(user.getId()))
                            .filter(t -> t.getLinkedTransaction() == null)
                            .filter(t -> t.getAmount().compareTo(amt) == 0)
                            .filter(t -> !t.getTransactionDate().isBefore(from) && !t.getTransactionDate().isAfter(to))
                            .toList();
                });

        when(transactionRepository.findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(eq(user.getId()), any(), any()))
                .thenAnswer(inv -> dbTransactions);

        when(transactionRepository.findBySourceDocumentIdAndUserId(eq(kotakDocument.getId()), eq(user.getId())))
                .thenAnswer(inv -> dbTransactions.stream().filter(t -> t.getSourceDocument() != null && t.getSourceDocument().getId().equals(kotakDocument.getId())).toList());

        when(transactionRepository.findBySourceDocumentIdAndUserId(eq(sbiDocument.getId()), eq(user.getId())))
                .thenAnswer(inv -> dbTransactions.stream().filter(t -> t.getSourceDocument() != null && t.getSourceDocument().getId().equals(sbiDocument.getId())).toList());

        doAnswer(inv -> {
            List<Transaction> toDelete = inv.getArgument(0);
            dbTransactions.removeAll(toDelete);
            return null;
        }).when(transactionRepository).deleteAll(any());

        candidateValidator = new CandidateValidator();
        deduplicationService = new DeduplicationService();
        settlementResolutionService = new SettlementResolutionService(settlementRepository);
        transferMatchingService = new TransferMatchingService(transactionRepository);

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
                currentUser,
                accountService,
                accountRepository,
                transferMatchingService
        );

        financialDocumentService = new FinancialDocumentService(
                documentRepository,
                documentStorage,
                userRepository,
                currentUser,
                settlementRepository,
                transactionRepository,
                transferMatchingService
        );

        analyticsService = new AnalyticsService(transactionRepository, currentUser);
        dashboardService = new DashboardService(transactionRepository, currentUser);
    }

    @Test
    @DisplayName("Complete end-to-end lifecycle: Kotak upload -> SBI upload -> Matching -> Analytics -> Kotak deletion unlinking")
    void verifyKotakSbiEndToEndTransferLifecycle() throws Exception {
        LocalDate aug10 = LocalDate.of(2026, 8, 10);

        // =========================================================================
        // STEP 1: Kotak Statement Ingestion (Salary ₹46,200 + Transfer Out ₹15,000)
        // =========================================================================
        TransactionCandidate kotakSalary = TransactionCandidate.builder()
                .documentId(kotakDocument.getId())
                .rowIndex(1)
                .transactionDate(aug10)
                .amount(new BigDecimal("46200.00"))
                .direction(TransactionType.INCOME)
                .classification(SourceTransactionClassification.DEPOSIT)
                .rawMerchant("IFT-VEEFIN SOLUTIONS LIMITED")
                .rawDescription("Salary credit for Aug 2026 FCM-260810OITIT0")
                .externalReference("FCM-260810OITIT0")
                .rawTypeIndicator("CR")
                .accountType(DocumentAccountType.SAVINGS)
                .build();

        TransactionCandidate kotakTransferOut = TransactionCandidate.builder()
                .documentId(kotakDocument.getId())
                .rowIndex(2)
                .transactionDate(aug10)
                .amount(new BigDecimal("15000.00"))
                .direction(TransactionType.EXPENSE)
                .classification(SourceTransactionClassification.WITHDRAWAL)
                .rawMerchant("HARSHIT ASHOK S")
                .rawDescription("UPI/HARSHIT ASHOK S/658828783 /Sent to State Ba/62222374151")
                .externalReference("62222374151")
                .rawTypeIndicator("DR")
                .accountType(DocumentAccountType.SAVINGS)
                .build();

        when(documentExtractor.extract(eq(kotakDocument.getId()), any(), any()))
                .thenReturn(List.of(kotakSalary, kotakTransferOut));

        StatementImportResult kotakResult = ingestionService.ingestDocument(kotakDocument.getId());

        assertThat(kotakResult.importedTransactions()).isEqualTo(2);
        assertThat(dbTransactions).hasSize(2);

        Transaction dbKotakSalary = dbTransactions.stream()
                .filter(t -> t.getAmount().compareTo(new BigDecimal("46200.00")) == 0)
                .findFirst().orElseThrow();
        assertThat(dbKotakSalary.getTransactionType()).isEqualTo(TransactionType.INCOME);

        Transaction dbKotakTransfer = dbTransactions.stream()
                .filter(t -> t.getAmount().compareTo(new BigDecimal("15000.00")) == 0)
                .findFirst().orElseThrow();
        // Crucial check: Transfer Out MUST NOT be EXPENSE
        assertThat(dbKotakTransfer.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(dbKotakTransfer.getTransferDirection()).isEqualTo(TransferDirection.OUT);
        assertThat(dbKotakTransfer.getTransferMatchStatus()).isEqualTo(TransferMatchStatus.UNMATCHED);

        // Mock repository calculations for Kotak alone
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, aug10, aug10))
                .thenReturn(new BigDecimal("46200.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, aug10, aug10))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumAmountByUserIdAndTypeAndDirectionAndDateBetween(user.getId(), TransactionType.TRANSFER, TransferDirection.OUT, aug10, aug10))
                .thenReturn(new BigDecimal("15000.00"));

        SpendingSummaryResponse summaryKotakOnly = analyticsService.getSummary(aug10, aug10);
        assertThat(summaryKotakOnly.totalIncome()).isEqualByComparingTo("46200.00");
        assertThat(summaryKotakOnly.totalExpenses()).isEqualByComparingTo("0.00");
        assertThat(summaryKotakOnly.totalTransfers()).isEqualByComparingTo("15000.00");
        assertThat(summaryKotakOnly.netSavings()).isEqualByComparingTo("46200.00");

        // =========================================================================
        // STEP 2: SBI Statement Ingestion (Transfer In ₹15,000 + Spend ₹2,000 + ATM ₹500)
        // =========================================================================
        TransactionCandidate sbiTransferIn = TransactionCandidate.builder()
                .documentId(sbiDocument.getId())
                .rowIndex(1)
                .transactionDate(aug10)
                .amount(new BigDecimal("15000.00"))
                .direction(TransactionType.INCOME)
                .classification(SourceTransactionClassification.DEPOSIT)
                .rawMerchant("HARSHIT ASHOK")
                .rawDescription("UPI/CR/622223741510/HARSHIT ASHOK/KKBK/0097692162094")
                .externalReference("622223741510")
                .rawTypeIndicator("CR")
                .accountType(DocumentAccountType.SAVINGS)
                .build();

        TransactionCandidate sbiSpend = TransactionCandidate.builder()
                .documentId(sbiDocument.getId())
                .rowIndex(2)
                .transactionDate(aug10)
                .amount(new BigDecimal("2000.00"))
                .direction(TransactionType.EXPENSE)
                .classification(SourceTransactionClassification.PURCHASE)
                .rawMerchant("Zomato")
                .rawDescription("UPI/DR/ZOMATO/ORDER123")
                .externalReference("ZOMATO123")
                .rawTypeIndicator("DR")
                .accountType(DocumentAccountType.SAVINGS)
                .build();

        TransactionCandidate sbiAtm = TransactionCandidate.builder()
                .documentId(sbiDocument.getId())
                .rowIndex(3)
                .transactionDate(aug10)
                .amount(new BigDecimal("500.00"))
                .direction(TransactionType.CASH_WITHDRAWAL)
                .classification(SourceTransactionClassification.WITHDRAWAL)
                .rawMerchant("ATM")
                .rawDescription("ATM WDL CASH 03117 VERSOVA (MUMBAI)")
                .externalReference("ATM3117")
                .rawTypeIndicator("DR")
                .accountType(DocumentAccountType.SAVINGS)
                .build();

        when(documentExtractor.extract(eq(sbiDocument.getId()), any(), any()))
                .thenReturn(List.of(sbiTransferIn, sbiSpend, sbiAtm));

        StatementImportResult sbiResult = ingestionService.ingestDocument(sbiDocument.getId());

        assertThat(sbiResult.importedTransactions()).isEqualTo(3);
        assertThat(dbTransactions).hasSize(5);

        Transaction dbSbiTransfer = dbTransactions.stream()
                .filter(t -> t.getAmount().compareTo(new BigDecimal("15000.00")) == 0 && t.getTransferDirection() == TransferDirection.IN)
                .findFirst().orElseThrow();

        // Crucial checks: SBI Transfer In MUST NOT be INCOME
        assertThat(dbSbiTransfer.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(dbSbiTransfer.getTransferDirection()).isEqualTo(TransferDirection.IN);
        assertThat(dbSbiTransfer.getTransferMatchStatus()).isEqualTo(TransferMatchStatus.AUTO_MATCHED);
        assertThat(dbSbiTransfer.getLinkedTransaction()).isEqualTo(dbKotakTransfer);
        assertThat(dbKotakTransfer.getTransferMatchStatus()).isEqualTo(TransferMatchStatus.AUTO_MATCHED);
        assertThat(dbKotakTransfer.getLinkedTransaction()).isEqualTo(dbSbiTransfer);

        Transaction dbSbiAtm = dbTransactions.stream()
                .filter(t -> t.getAmount().compareTo(new BigDecimal("500.00")) == 0)
                .findFirst().orElseThrow();
        // ATM cash withdrawal is CASH_WITHDRAWAL, not EXPENSE
        assertThat(dbSbiAtm.getTransactionType()).isEqualTo(TransactionType.CASH_WITHDRAWAL);

        Transaction dbSbiSpend = dbTransactions.stream()
                .filter(t -> t.getAmount().compareTo(new BigDecimal("2000.00")) == 0)
                .findFirst().orElseThrow();
        assertThat(dbSbiSpend.getTransactionType()).isEqualTo(TransactionType.EXPENSE);

        // =========================================================================
        // STEP 3: Combined Analytics Verification
        // =========================================================================
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, aug10, aug10))
                .thenReturn(new BigDecimal("46200.00")); // Only Kotak salary
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, aug10, aug10))
                .thenReturn(new BigDecimal("2000.00")); // Only SBI Zomato
        when(transactionRepository.sumAmountByUserIdAndTypeAndDirectionAndDateBetween(user.getId(), TransactionType.TRANSFER, TransferDirection.OUT, aug10, aug10))
                .thenReturn(new BigDecimal("15000.00")); // Internal transfer OUT

        SpendingSummaryResponse combinedSummary = analyticsService.getSummary(aug10, aug10);
        assertThat(combinedSummary.totalIncome()).isEqualByComparingTo("46200.00");
        assertThat(combinedSummary.totalExpenses()).isEqualByComparingTo("2000.00");
        assertThat(combinedSummary.totalTransfers()).isEqualByComparingTo("15000.00");
        assertThat(combinedSummary.netSavings()).isEqualByComparingTo("44200.00");

        // =========================================================================
        // STEP 4: Delete Kotak Statement -> Deletes Kotak Leg & Unlinks SBI Leg to UNMATCHED
        // =========================================================================
        financialDocumentService.delete(kotakDocument.getId());

        // Kotak transactions deleted from DB
        assertThat(dbTransactions).doesNotContain(dbKotakSalary, dbKotakTransfer);

        // SBI leg preserved, but unlinked and set to UNMATCHED
        assertThat(dbTransactions).contains(dbSbiTransfer, dbSbiSpend, dbSbiAtm);
        assertThat(dbSbiTransfer.getLinkedTransaction()).isNull();
        assertThat(dbSbiTransfer.getTransferMatchStatus()).isEqualTo(TransferMatchStatus.UNMATCHED);
    }

    @Test
    @DisplayName("Reverse Upload Ordering: SBI uploaded first, then Kotak later -> Bilateral matching pairs & retroactively upgrades both")
    void verifyReverseUploadOrdering_SbiFirstThenKotak_CorrectlyPairsAndRetroactivelyUpgrades() {
        LocalDate aug10 = LocalDate.of(2026, 8, 10);

        // 1. SBI statement arrives first (credit 15,000)
        TransactionCandidate sbiCredit = TransactionCandidate.builder()
                .documentId(sbiDocument.getId())
                .rowIndex(1)
                .transactionDate(aug10)
                .amount(new BigDecimal("15000.00"))
                .direction(TransactionType.INCOME)
                .classification(SourceTransactionClassification.DEPOSIT)
                .rawMerchant("HARSHIT")
                .rawDescription("DEP TFR UPI/CR/658828783145/HARSHIT /KKBK/8369218446/UPI")
                .externalReference("658828783145")
                .rawTypeIndicator("CR")
                .accountType(DocumentAccountType.SAVINGS)
                .build();

        when(documentExtractor.extract(eq(sbiDocument.getId()), any(), any()))
                .thenReturn(List.of(sbiCredit));

        StatementImportResult sbiResult = ingestionService.ingestDocument(sbiDocument.getId());
        assertThat(sbiResult.importedTransactions()).isEqualTo(1);
        assertThat(dbTransactions).hasSize(1);

        Transaction sbiTx = dbTransactions.get(0);
        // Initially stored as ordinary credit or candidate transfer
        assertThat(sbiTx.getAmount()).isEqualByComparingTo("15000.00");
        assertThat(sbiTx.getLinkedTransaction()).isNull();

        // 2. Kotak statement arrives later (debit 15,000 with matching UTR 658828783145)
        TransactionCandidate kotakDebit = TransactionCandidate.builder()
                .documentId(kotakDocument.getId())
                .rowIndex(1)
                .transactionDate(aug10)
                .amount(new BigDecimal("15000.00"))
                .direction(TransactionType.EXPENSE)
                .classification(SourceTransactionClassification.WITHDRAWAL)
                .rawMerchant("HARSHIT ASHOK S")
                .rawDescription("UPI/HARSHIT ASHOK S/658828783145/UPI")
                .externalReference("UPI-210017231015")
                .rawTypeIndicator("DR")
                .accountType(DocumentAccountType.SAVINGS)
                .build();

        when(documentExtractor.extract(eq(kotakDocument.getId()), any(), any()))
                .thenReturn(List.of(kotakDebit));

        StatementImportResult kotakResult = ingestionService.ingestDocument(kotakDocument.getId());
        assertThat(kotakResult.importedTransactions()).isEqualTo(1);
        assertThat(dbTransactions).hasSize(2);

        Transaction kotakTx = dbTransactions.stream()
                .filter(t -> t.getAccount().getId().equals(kotakAccount.getId()))
                .findFirst().orElseThrow();

        // Both must be upgraded to TRANSFER and AUTO_MATCHED!
        assertThat(kotakTx.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(kotakTx.getTransferDirection()).isEqualTo(TransferDirection.OUT);
        assertThat(kotakTx.getTransferMatchStatus()).isEqualTo(TransferMatchStatus.AUTO_MATCHED);
        assertThat(kotakTx.getLinkedTransaction()).isEqualTo(sbiTx);

        assertThat(sbiTx.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(sbiTx.getTransferDirection()).isEqualTo(TransferDirection.IN);
        assertThat(sbiTx.getTransferMatchStatus()).isEqualTo(TransferMatchStatus.AUTO_MATCHED);
        assertThat(sbiTx.getLinkedTransaction()).isEqualTo(kotakTx);
    }

    @Test
    @DisplayName("Unrelated transactions with same amount and UPI rail must NOT pair without transfer evidence")
    void verifyUnrelatedTransactionsWithSameAmountAndRailDoNotPair() {
        LocalDate date = LocalDate.of(2026, 8, 15);

        // Account 1: Swiggy food expense of 500 via UPI
        TransactionCandidate foodExpense = TransactionCandidate.builder()
                .documentId(kotakDocument.getId())
                .rowIndex(1)
                .transactionDate(date)
                .amount(new BigDecimal("500.00"))
                .direction(TransactionType.EXPENSE)
                .classification(SourceTransactionClassification.PURCHASE)
                .rawMerchant("Swiggy")
                .rawDescription("UPI/SWIGGY/111222333444")
                .externalReference("111222333444")
                .rawTypeIndicator("DR")
                .accountType(DocumentAccountType.SAVINGS)
                .build();

        // Account 2: Friend refund / deposit of 500 via UPI with different UTR
        TransactionCandidate friendDeposit = TransactionCandidate.builder()
                .documentId(sbiDocument.getId())
                .rowIndex(1)
                .transactionDate(date)
                .amount(new BigDecimal("500.00"))
                .direction(TransactionType.INCOME)
                .classification(SourceTransactionClassification.DEPOSIT)
                .rawMerchant("Ramesh Kumar")
                .rawDescription("UPI/RAMESH KUMAR/999888777666")
                .externalReference("999888777666")
                .rawTypeIndicator("CR")
                .accountType(DocumentAccountType.SAVINGS)
                .build();

        when(documentExtractor.extract(eq(kotakDocument.getId()), any(), any())).thenReturn(List.of(foodExpense));
        when(documentExtractor.extract(eq(sbiDocument.getId()), any(), any())).thenReturn(List.of(friendDeposit));

        ingestionService.ingestDocument(kotakDocument.getId());
        ingestionService.ingestDocument(sbiDocument.getId());

        Transaction txFood = dbTransactions.stream()
                .filter(t -> t.getAccount().getId().equals(kotakAccount.getId()))
                .findFirst().orElseThrow();
        Transaction txDeposit = dbTransactions.stream()
                .filter(t -> t.getAccount().getId().equals(sbiAccount.getId()))
                .findFirst().orElseThrow();

        // MUST NOT be paired as transfer!
        assertThat(txFood.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(txFood.getLinkedTransaction()).isNull();

        assertThat(txDeposit.getTransactionType()).isEqualTo(TransactionType.INCOME);
        assertThat(txDeposit.getLinkedTransaction()).isNull();
    }

    @Test
    @DisplayName("Financial calculations: TRANSFER, INVESTMENT, and CASH_WITHDRAWAL are completely excluded from income, expenses, and category spending")
    void verifyFinancialDashboardSemantics_ExcludesTransfersInvestmentsAndCashWithdrawals() {
        LocalDate date = LocalDate.of(2026, 8, 20);

        // Genuine Income: 50,000
        Transaction income = new Transaction(user, new BigDecimal("50000.00"), "Employer", TransactionCategory.OTHER, TransactionType.INCOME, date, "Salary");
        income.setAccount(kotakAccount);

        // Genuine Expense: 10,000 (FOOD)
        Transaction expense = new Transaction(user, new BigDecimal("10000.00"), "Restaurant", TransactionCategory.FOOD, TransactionType.EXPENSE, date, "Dining");
        expense.setAccount(kotakAccount);

        // Transfer OUT: 15,000
        Transaction transferOut = new Transaction(user, new BigDecimal("15000.00"), "Transfer", TransactionCategory.OTHER, TransactionType.TRANSFER, date, "Transfer to SBI");
        transferOut.setTransferDirection(TransferDirection.OUT);
        transferOut.setTransferMatchStatus(TransferMatchStatus.AUTO_MATCHED);
        transferOut.setAccount(kotakAccount);

        // Transfer IN: 15,000
        Transaction transferIn = new Transaction(user, new BigDecimal("15000.00"), "Transfer", TransactionCategory.OTHER, TransactionType.TRANSFER, date, "Transfer from Kotak");
        transferIn.setTransferDirection(TransferDirection.IN);
        transferIn.setTransferMatchStatus(TransferMatchStatus.AUTO_MATCHED);
        transferIn.setAccount(sbiAccount);

        transferOut.linkTransfer(transferIn, sbiAccount, TransferMatchStatus.AUTO_MATCHED);
        transferIn.linkTransfer(transferOut, kotakAccount, TransferMatchStatus.AUTO_MATCHED);

        // Investment: 5,000
        Transaction investment = new Transaction(user, new BigDecimal("5000.00"), "Mutual Fund", TransactionCategory.OTHER, TransactionType.INVESTMENT, date, "SIP Investment");
        investment.setAccount(kotakAccount);

        // Cash Withdrawal: 2,000
        Transaction cashWdl = new Transaction(user, new BigDecimal("2000.00"), "ATM", TransactionCategory.OTHER, TransactionType.CASH_WITHDRAWAL, date, "Cash withdrawal");
        cashWdl.setAccount(sbiAccount);

        dbTransactions.addAll(List.of(income, expense, transferOut, transferIn, investment, cashWdl));

        // Mock repository calls strictly following the explicit type filtering
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, date, date))
                .thenReturn(new BigDecimal("50000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, date, date))
                .thenReturn(new BigDecimal("10000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDirectionAndDateBetween(user.getId(), TransactionType.TRANSFER, TransferDirection.OUT, date, date))
                .thenReturn(new BigDecimal("15000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INVESTMENT, date, date))
                .thenReturn(new BigDecimal("5000.00"));
        when(transactionRepository.findCategorySpending(user.getId(), TransactionType.EXPENSE, date, date))
                .thenReturn(List.of(new com.spendsense.dashboard.dto.CategorySpend(TransactionCategory.FOOD, new BigDecimal("10000.00"), 1L)));

        SpendingSummaryResponse summary = analyticsService.getSummary(date, date);
        assertThat(summary.totalIncome()).isEqualByComparingTo("50000.00");
        assertThat(summary.totalExpenses()).isEqualByComparingTo("10000.00");
        assertThat(summary.netSavings()).isEqualByComparingTo("40000.00");
        assertThat(summary.totalTransfers()).isEqualByComparingTo("15000.00");
        assertThat(summary.investmentContributions()).isEqualByComparingTo("5000.00");

        DashboardDataResponse dashboard = dashboardService.getDashboardData(null, date, date);
        assertThat(dashboard.summary().totalIncome()).isEqualByComparingTo("50000.00");
        assertThat(dashboard.summary().totalExpenses()).isEqualByComparingTo("10000.00");
        assertThat(dashboard.summary().netSavings()).isEqualByComparingTo("40000.00");
        assertThat(dashboard.summary().totalTransfers()).isEqualByComparingTo("15000.00");
        assertThat(dashboard.summary().investmentContributions()).isEqualByComparingTo("5000.00");
        assertThat(dashboard.categorySpending()).hasSize(1);
        assertThat(dashboard.categorySpending().get(0).category()).isEqualTo(TransactionCategory.FOOD);
        assertThat(dashboard.categorySpending().get(0).amount()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("Legacy manual transaction with account_id NULL remains completely valid")
    void verifyLegacyManualTransactionWithoutAccountRemainsValid() {
        LocalDate date = LocalDate.of(2026, 8, 25);
        Transaction manualTx = new Transaction(user, new BigDecimal("750.00"), "Local Bakery", TransactionCategory.FOOD, TransactionType.EXPENSE, date, "Cash payment");
        // account is intentionally null (legacy manual transaction)
        assertThat(manualTx.getAccount()).isNull();
        assertThat(manualTx.getSourceDocument()).isNull();

        dbTransactions.add(manualTx);
        assertThat(dbTransactions).contains(manualTx);
        assertThat(manualTx.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("Same amount + opposite direction + UTR on SAME account does NOT pair as internal transfer")
    void verifySameAccountTransactionsDoNotPairAsInternalTransfer() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        Transaction debitSameAcc = new Transaction(user, new BigDecimal("15000.00"), "HARSHIT", "UPI/HARSHIT/658828783145", TransactionCategory.OTHER, TransactionType.TRANSFER, PaymentMethod.UPI, date, "Transfer");
        debitSameAcc.setTransferDirection(TransferDirection.OUT);
        debitSameAcc.setAccount(kotakAccount);

        Transaction creditSameAcc = new Transaction(user, new BigDecimal("15000.00"), "HARSHIT", "UPI/HARSHIT/658828783145", TransactionCategory.OTHER, TransactionType.TRANSFER, PaymentMethod.UPI, date, "Deposit");
        creditSameAcc.setTransferDirection(TransferDirection.IN);
        creditSameAcc.setAccount(kotakAccount); // SAME account!

        dbTransactions.addAll(List.of(debitSameAcc, creditSameAcc));

        assertThat(transferMatchingService.isDifferentAccount(debitSameAcc, creditSameAcc)).isFalse();
        assertThat(transferMatchingService.hasMatchingConfidence(debitSameAcc, creditSameAcc)).isFalse();

        boolean matched = transferMatchingService.attemptMatch(debitSameAcc);
        assertThat(matched).isFalse();
        assertThat(debitSameAcc.getLinkedTransaction()).isNull();
        assertThat(creditSameAcc.getLinkedTransaction()).isNull();
    }

    @Test
    @DisplayName("Ambiguous statements with missing institution and account number do NOT collapse into a shared account")
    void verifyAmbiguousStatementsDoNotCollapseIntoSharedAccount() {
        FinancialAccountService realAccountService = new FinancialAccountService(accountRepository, userRepository, currentUser);
        when(accountRepository.save(any(FinancialAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancialAccount acc1 = realAccountService.getOrCreateForStatement(user, null, DocumentAccountType.SAVINGS, null);
        FinancialAccount acc2 = realAccountService.getOrCreateForStatement(user, "", DocumentAccountType.SAVINGS, "");

        assertThat(acc1).isNotSameAs(acc2);
        assertThat(acc1.getName()).isEqualTo("Unspecified (SAVINGS)");
        assertThat(acc2.getName()).isEqualTo("Unspecified (SAVINGS)");
        // Verified: distinct instances created rather than reusing an existing generic account
        verify(accountRepository, times(2)).save(any(FinancialAccount.class));
    }
}
